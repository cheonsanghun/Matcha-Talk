package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.FollowEventMessage;
import net.datasa.project01.domain.dto.FollowRequestCreateDto;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.domain.dto.FollowSummaryDto;
import net.datasa.project01.domain.entity.FollowRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowRequestRepository;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FollowService {

    private static final int RECENT_MESSAGE_LIMIT = 50;

    private final FollowRequestRepository followRequestRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public FollowResponseDto requestFollow(String loginId, FollowRequestCreateDto dto) {
        if (dto == null || dto.getRoomId() == null) {
            throw new IllegalArgumentException("팔로우를 요청할 채팅방을 선택해 주세요.");
        }
        User requester = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방 정보를 확인할 수 없습니다."));

        if (room.getRoomType() != Room.RoomType.PRIVATE) {
            throw new IllegalArgumentException("팔로우는 1:1 매칭 채팅에서만 신청할 수 있습니다.");
        }

        Participants participants = resolveParticipants(room, requester);
        User receiver = participants.partner();

        Optional<FollowRequest> latestOptional = followRequestRepository.findTopByRoomOrderByCreatedAtDesc(room);
        if (latestOptional.isPresent()) {
            FollowRequest latest = latestOptional.get();
            if (latest.getStatus() == FollowRequest.Status.PENDING) {
                String message = latest.getRequester().getUserPid().equals(requester.getUserPid())
                        ? "이미 팔로우 요청을 보냈습니다."
                        : String.format("%s님이 팔로우 요청을 보냈습니다.", latest.getRequester().getNickName());
                FollowResponseDto response = FollowResponseDto.from(latest, requester, message);
                if (!latest.getRequester().getUserPid().equals(requester.getUserPid())) {
                    // 상대 요청 대기 중임을 알리기 위해 이벤트만 재전송
                    sendFollowEvent(requester, FollowEventMessage.EventType.REQUESTED, latest, message);
                }
                return response;
            }
            if (latest.getStatus() == FollowRequest.Status.ACCEPTED) {
                String message = "이미 서로 팔로우 관계입니다.";
                return FollowResponseDto.from(latest, requester, message);
            }
        }

        FollowRequest newRequest = followRequestRepository.save(FollowRequest.builder()
                .room(room)
                .requester(requester)
                .receiver(receiver)
                .status(FollowRequest.Status.PENDING)
                .build());

        String requesterMessage = "팔로우 요청을 보냈습니다.";
        String receiverMessage = String.format("%s님이 팔로우를 요청했습니다.", requester.getNickName());

        sendFollowEvent(requester, FollowEventMessage.EventType.REQUESTED, newRequest, requesterMessage);
        sendFollowEvent(receiver, FollowEventMessage.EventType.REQUESTED, newRequest, receiverMessage);

        return FollowResponseDto.from(newRequest, requester, requesterMessage);
    }

    public FollowResponseDto respondToFollow(Long followRequestId, String loginId, boolean accept) {
        FollowRequest request = followRequestRepository.findById(followRequestId)
                .orElseThrow(() -> new IllegalArgumentException("팔로우 요청을 찾을 수 없습니다."));

        if (request.getStatus() != FollowRequest.Status.PENDING) {
            throw new IllegalStateException("이미 처리된 팔로우 요청입니다.");
        }

        if (!request.getReceiver().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("해당 팔로우 요청에 응답할 권한이 없습니다.");
        }

        User receiver = request.getReceiver();
        User requester = request.getRequester();

        request.setStatus(accept ? FollowRequest.Status.ACCEPTED : FollowRequest.Status.DECLINED);
        request.setRespondedAt(LocalDateTime.now());

        String receiverMessage = accept ? "팔로우를 수락했습니다." : "팔로우 요청을 거절했습니다.";
        String requesterMessage = accept
                ? String.format("%s님이 팔로우를 수락했습니다.", receiver.getNickName())
                : String.format("%s님이 팔로우 요청을 거절했습니다.", receiver.getNickName());

        FollowEventMessage.EventType eventType = accept
                ? FollowEventMessage.EventType.ACCEPTED
                : FollowEventMessage.EventType.DECLINED;

        sendFollowEvent(receiver, eventType, request, receiverMessage);
        sendFollowEvent(requester, eventType, request, requesterMessage);

        return FollowResponseDto.from(request, receiver, receiverMessage);
    }

    @Transactional(readOnly = true)
    public FollowResponseDto getFollowStatus(String loginId, Long roomId) {
        User viewer = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 정보를 확인할 수 없습니다."));

        Participants participants = resolveParticipants(room, viewer);
        Optional<FollowRequest> latestOptional = followRequestRepository.findTopByRoomOrderByCreatedAtDesc(room);

        if (latestOptional.isEmpty()) {
            return FollowResponseDto.none(room, viewer, participants.partner());
        }

        FollowRequest latest = latestOptional.get();
        if (!latest.involves(viewer)) {
            throw new IllegalArgumentException("팔로우 요청 정보를 확인할 수 없습니다.");
        }
        return FollowResponseDto.from(latest, viewer);
    }

    @Transactional(readOnly = true)
    public List<FollowSummaryDto> getAcceptedFollows(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<FollowRequest> accepted = followRequestRepository.findByUserAndStatus(user, FollowRequest.Status.ACCEPTED);
        Map<Long, FollowRequest> latestByRoom = new LinkedHashMap<>();
        for (FollowRequest request : accepted) {
            Room room = request.getRoom();
            if (room == null) {
                continue;
            }
            Long roomId = room.getRoomId();
            if (roomId == null) {
                continue;
            }
            latestByRoom.putIfAbsent(roomId, request);
        }

        List<FollowSummaryDto> summaries = new ArrayList<>();
        for (FollowRequest request : latestByRoom.values()) {
            Room room = request.getRoom();
            if (room == null) {
                continue;
            }
            boolean viewerIsRequester = request.getRequester() != null
                    && request.getRequester().getUserPid() != null
                    && request.getRequester().getUserPid().equals(user.getUserPid());
            User partner = viewerIsRequester ? request.getReceiver() : request.getRequester();

            List<ChatMessageResponseDto> messages = fetchRecentMessages(room);

            FollowSummaryDto summary = FollowSummaryDto.builder()
                    .followRequestId(request.getFollowRequestId())
                    .roomId(room.getRoomId())
                    .partnerLoginId(partner != null ? partner.getLoginId() : null)
                    .partnerNickName(partner != null ? partner.getNickName() : null)
                    .acceptedAt(request.getRespondedAt())
                    .recentMessages(messages)
                    .build();
            summaries.add(summary);
        }
        return summaries;
    }

    private List<ChatMessageResponseDto> fetchRecentMessages(Room room) {
        return roomMessageRepository.findByRoomOrderByCreatedAtDesc(
                        room,
                        PageRequest.of(0, RECENT_MESSAGE_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toChatMessageDto)
                .filter(dto -> dto != null)
                .sorted((a, b) -> {
                    LocalDateTime left = a.getSentAt();
                    LocalDateTime right = b.getSentAt();
                    if (left == null && right == null) {
                        return 0;
                    }
                    if (left == null) {
                        return -1;
                    }
                    if (right == null) {
                        return 1;
                    }
                    return left.compareTo(right);
                })
                .collect(Collectors.toList());
    }

    private ChatMessageResponseDto toChatMessageDto(RoomMessage message) {
        if (message == null) {
            return null;
        }
        String fileUrl = null;
        if (message.getMessageId() != null && message.getFilePath() != null) {
            fileUrl = "/api/chat/files/" + message.getMessageId();
        }
        return ChatMessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom() != null ? message.getRoom().getRoomId() : null)
                .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                .senderNickName(message.getSender() != null ? message.getSender().getNickName() : null)
                .content(message.getTextContent())
                .translatedContent(null)
                .contentType(message.getContentType() != null ? message.getContentType().name() : null)
                .fileName(message.getFileName())
                .fileUrl(fileUrl)
                .mimeType(message.getMimeType())
                .sizeBytes(message.getSizeBytes())
                .sentAt(message.getCreatedAt())
                .build();
    }

    private Participants resolveParticipants(Room room, User viewer) {
        List<RoomMember> members = roomMemberRepository.findByRoom(room);
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("채팅방 참여자 정보를 찾을 수 없습니다.");
        }

        RoomMember viewerMember = members.stream()
                .filter(member -> member.getUser() != null
                        && member.getUser().getUserPid() != null
                        && member.getUser().getUserPid().equals(viewer.getUserPid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 채팅방 참여자가 아닙니다."));

        RoomMember partnerMember = members.stream()
                .filter(member -> member.getUser() != null
                        && !member.getUser().getUserPid().equals(viewer.getUserPid()))
                .filter(member -> member.getLeftAt() == null)
                .findFirst()
                .orElse(null);

        if (partnerMember == null) {
            partnerMember = members.stream()
                    .filter(member -> member.getUser() != null
                            && !member.getUser().getUserPid().equals(viewer.getUserPid()))
                    .findFirst()
                    .orElse(null);
        }

        if (partnerMember == null || partnerMember.getUser() == null) {
            throw new IllegalStateException("상대방 정보를 찾을 수 없습니다.");
        }

        return new Participants(viewerMember.getUser(), partnerMember.getUser());
    }

    private void sendFollowEvent(User target, FollowEventMessage.EventType eventType,
                                 FollowRequest request, String message) {
        if (target == null) {
            return;
        }
        FollowResponseDto payload = FollowResponseDto.from(request, target, message);
        FollowEventMessage event = FollowEventMessage.builder()
                .eventType(eventType)
                .follow(payload)
                .message(message)
                .build();
        messagingTemplate.convertAndSendToUser(target.getLoginId(), "/queue/follow-events", event);
    }

    private record Participants(User viewer, User partner) {
    }
}
