package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.websocket.RealTimeMessagingService;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.service.support.MatchRequestSanitizer;
import net.datasa.project01.repository.FollowRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.datasa.project01.domain.entity.Room.RoomType;

import net.datasa.project01.service.TranslationService;


@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

        private final RoomRepository roomRepository;
        private final RoomMemberRepository roomMemberRepository;
        private final UserRepository userRepository;
        private final RoomMessageRepository roomMessageRepository;
        private final MatchRequestRepository matchRequestRepository;
        private final FollowRepository followRepository;
        private final TranslationService translationService;
        private final RealTimeMessagingService messagingService;

        private static final String PROMOTION_REASON_MUTUAL_FOLLOW = "MUTUAL_FOLLOW";

        private final Path attachmentBasePath = Paths.get("uploads", "attachments");

        // TODO: 알림 서비스 추가 (Push Notification)
        // private final NotificationService notificationService;
        // TODO: 파일 업로드 서비스 추가
        // private final FileUploadService fileUploadService;
        // TODO: 커비너 세션 및 캐시 관리
        // private final RedisTemplate<String, Object> redisTemplate;

        @Transactional
        public Room createGroupRoom(String loginId) {
                // TODO: 방 이름 설정 기능 추가
                // TODO: 비밀번호 보호 기능 추가
                // TODO: 초대 전용 방 기능 추가
                User creator = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 새로운 Room 엔티티를 생성하고 데이터베이스에 저장
        Room newRoom = Room.builder()
                .roomType(Room.RoomType.GROUP) // Enum 타입 직접 사용
                .capacity(4) // 그룹방의 최대 인원은 4명으로 고정
                .build();
        roomRepository.save(newRoom);

        // 3. 방을 만든 사람을 해당 방의 첫 멤버이자 방장(HOST)으로 추가
        RoomMember newMember = RoomMember.builder()
                .room(newRoom)
                .user(creator)
                .role("HOST") // DB 스키마에 정의된 enum 값
                .build();
        roomMemberRepository.save(newMember);

                // TODO: 방 생성 알림 전송
                // TODO: 방 생성 로그 기록
                return newRoom;
        }

        @Transactional
        public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
                User sender = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                Room room = roomRepository.findById(requestDto.getRoomId())
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build();
        roomMessageRepository.save(message);

        return toResponseDto(message);
        }
        @Transactional
        public Room createPrivateRoom(User user1, User user2) {
                // 1. 새로운 PRIVATE 타입의 방 생성
                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.PRIVATE)
                        .capacity(2)
                        .build();
                roomRepository.save(newRoom);

                // 2. 두 명의 사용자를 멤버로 추가
                RoomMember member1 = RoomMember.builder()
                        .room(newRoom)
                        .user(user1)
                        .role("MEMBER")
                        .build();
                
                RoomMember member2 = RoomMember.builder()
                        .room(newRoom)
                        .user(user2)
                        .role("MEMBER")
                        .build();
                
                roomMemberRepository.saveAll(java.util.List.of(member1, member2)); // 두 멤버를 한 번에 저장

                return newRoom;
        }

        @Transactional
        public Room createRandomRoom(User user1, User user2) {
                return createRandomMatchRoom(user1, user2);
        }

        @Transactional
        public Room createRandomMatchRoom(User user1, User user2) {
                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.RANDOM)
                        .capacity(2)
                        .createdFromRoom(null)
                        .promotedAt(null)
                        .promotedReason(null)
                        .build();
                roomRepository.save(newRoom);

                RoomMember member1 = RoomMember.builder()
                        .room(newRoom)
                        .user(user1)
                        .role("MEMBER")
                        .build();

                RoomMember member2 = RoomMember.builder()
                        .room(newRoom)
                        .user(user2)
                        .role("MEMBER")
                        .build();

                roomMemberRepository.saveAll(java.util.List.of(member1, member2));

                return newRoom;
        }

        @Transactional
        public Room promoteRandomRoom(Room room) {
                if (room == null || room.getRoomType() != Room.RoomType.RANDOM) {
                        return room;
                }

                Room origin = room.getCreatedFromRoom() != null ? room.getCreatedFromRoom() : room;
                room.markPromoted(Room.RoomType.PRIVATE, origin, PROMOTION_REASON_MUTUAL_FOLLOW, LocalDateTime.now());

                return roomRepository.save(room);
        }

        @Transactional(readOnly = true)
        public List<RoomListResponseDto> findRoomsByUser(String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                // 1. N+1 문제를 해결하기 위해 fetch join으로 사용자가 속한 모든 방과 멤버 정보를 한 번에 조회
                List<RoomMember> allMembersInMyRooms = roomMemberRepository.findAllRoomsAndMembersByUser(user);

                // 2. 조회된 멤버 목록을 '방(Room)' 기준으로 그룹핑
                Map<Room, List<RoomMember>> roomsGroupedByRoom = allMembersInMyRooms.stream()
                        .collect(Collectors.groupingBy(RoomMember::getRoom));

                // 3. 그룹핑된 데이터를 DTO로 변환
                return roomsGroupedByRoom.entrySet().stream()
                        .filter(entry -> !entry.getKey().isTemporary())
                        .map(entry -> RoomListResponseDto.fromEntity(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
        }

        @Transactional
        public RoomDetailResponseDto ensureDirectRoom(String loginId, Long targetUserPid) {
                if (targetUserPid == null) {
                        throw new IllegalArgumentException("대상 사용자를 선택해주세요.");
                }

                User requester = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                if (requester.getUserPid().equals(targetUserPid)) {
                        throw new IllegalArgumentException("자기 자신과는 채팅방을 만들 수 없습니다.");
                }

                User target = userRepository.findById(targetUserPid)
                        .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

                boolean hasAcceptedFollow = followRepository.findByFollowerAndFolloweeAndStatus(requester, target, Follow.FollowStatus.ACCEPTED)
                        .isPresent()
                        || followRepository.findByFollowerAndFolloweeAndStatus(target, requester, Follow.FollowStatus.ACCEPTED)
                        .isPresent();

                if (!hasAcceptedFollow) {
                        throw new IllegalStateException("상대방과 팔로우가 수락된 상태에서만 1:1 채팅을 시작할 수 있습니다.");
                }

                Room existing = findExistingDirectRoom(requester, target);
                Room resolved = existing != null ? existing : createPrivateRoom(requester, target);

                List<RoomMember> members = roomMemberRepository.findByRoom(resolved);
                return RoomDetailResponseDto.fromEntity(resolved, members);
        }

        @Transactional(readOnly = true)
        public RoomDetailResponseDto findRoomDetailsById(Long roomId, String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                // 사용자가 해당 방의 멤버인지 확인 (권한 검사)
                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("해당 채팅방에 접근할 권한이 없습니다."));

                // 방의 모든 멤버를 조회하여 상세 DTO 생성
                List<RoomMember> members = roomMemberRepository.findByRoom(room);

                return RoomDetailResponseDto.fromEntity(room, members);
        }

        @Transactional(readOnly = true)
        public java.util.List<String> findParticipantLoginIds(Long roomId) {
                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                return roomMemberRepository.findByRoom(room).stream()
                        .map(RoomMember::getUser)
                        .map(User::getLoginId)
                        .toList();
        }

        @Transactional
        public RoomCleanupResult cleanupTemporaryRoom(Long roomId, String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방 멤버만 정리할 수 있습니다."));

                boolean temporary = room.getRoomType() == Room.RoomType.RANDOM;
                if (!temporary) {
                        return new RoomCleanupResult(false, false, false, "ROOM_NOT_TEMPORARY");
                }

                List<RoomMember> members = roomMemberRepository.findByRoom(room);
                boolean mutualFollow = hasMutualFollow(members);
                if (mutualFollow) {
                        return new RoomCleanupResult(false, true, true, "MUTUAL_FOLLOW");
                }

                removeRoomWithDependencies(room, members);
                return new RoomCleanupResult(true, true, false, "DELETED");
        }

        @Transactional
        public ChatMessageResponseDto saveAttachment(Long roomId, MultipartFile file, String loginId) throws IOException {
                if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
                }

                User sender = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                roomMemberRepository.findByRoomAndUser(room, sender)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방에 속한 사용자만 파일을 전송할 수 있습니다."));

                Path uploadDir = ensureUploadDirectory(roomId);

                String originalName = StringUtils.hasText(file.getOriginalFilename())
                        ? file.getOriginalFilename()
                        : "attachment";
                String extension = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > -1 && dotIndex < originalName.length() - 1) {
                        extension = originalName.substring(dotIndex);
                }

                String storedName = UUID.randomUUID() + extension;
                Path storedPath = uploadDir.resolve(storedName);
                Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

                RoomMessage.ContentType contentType = determineContentType(file.getContentType());
                String placeholderText = contentType == RoomMessage.ContentType.IMAGE ? "이미지 첨부" : originalName;

                RoomMessage message = RoomMessage.builder()
                        .room(room)
                        .sender(sender)
                        .contentType(contentType)
                        .textContent(placeholderText)
                        .fileName(originalName)
                        .filePath(storedPath.toString())
                        .mimeType(file.getContentType())
                        .sizeBytes(file.getSize())
                        .build();
                roomMessageRepository.save(message);

                ChatMessageResponseDto response = toResponseDto(message);

                messagingService.broadcastToUsers(
                        findParticipantLoginIds(roomId),
                        "chat",
                        response
                );

                return response;
        }

        @Transactional(readOnly = true)
        public AttachmentResource loadAttachment(Long roomId, Long messageId, String loginId) throws IOException {
                User requester = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                RoomMessage message = roomMessageRepository.findById(messageId)
                        .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

                if (!message.getRoom().getRoomId().equals(roomId)) {
                        throw new IllegalArgumentException("요청한 파일이 해당 채팅방에 존재하지 않습니다.");
                }

                roomMemberRepository.findByRoomAndUser(message.getRoom(), requester)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방에 속한 사용자만 파일을 다운로드할 수 있습니다."));

                Path filePath = Paths.get(message.getFilePath());
                if (!Files.exists(filePath)) {
                        throw new IllegalArgumentException("파일이 삭제되었거나 존재하지 않습니다.");
                }

                Resource resource = new UrlResource(filePath.toUri());
                if (!resource.exists()) {
                        throw new IllegalArgumentException("파일을 찾을 수 없습니다.");
                }
                return new AttachmentResource(resource, message.getFileName(), message.getMimeType());
        }

        private ChatMessageResponseDto toResponseDto(RoomMessage message) {
                String downloadUrl = null;
                if (message.getContentType() != RoomMessage.ContentType.TEXT && StringUtils.hasText(message.getFilePath())) {
                        downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/api/rooms/")
                                .path(String.valueOf(message.getRoom().getRoomId()))
                                .path("/attachments/")
                                .path(String.valueOf(message.getMessageId()))
                                .toUriString();
                }

                return ChatMessageResponseDto.builder()
                        .roomId(message.getRoom().getRoomId())
                        .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                        .senderNickName(message.getSender() != null ? message.getSender().getNickName() : "시스템")
                        .senderLanguageCode(message.getSender() != null ? message.getSender().getLanguageCode() : null)
                        .content(message.getTextContent())
                        .contentType(message.getContentType().name())
                        .fileName(message.getFileName())
                        .fileUrl(downloadUrl)
                        .mimeType(message.getMimeType())
                        .sizeBytes(message.getSizeBytes())
                        .sentAt(message.getCreatedAt())
                        .build();
        }

        private boolean hasMutualFollow(List<RoomMember> members) {
                if (members == null) {
                        return false;
                }

                Map<Long, User> distinct = new LinkedHashMap<>();
                for (RoomMember member : members) {
                        User participant = member.getUser();
                        if (participant != null) {
                                distinct.putIfAbsent(participant.getUserPid(), participant);
                        }
                }

                Iterator<User> iterator = distinct.values().iterator();
                if (!iterator.hasNext()) {
                        return false;
                }
                User first = iterator.next();
                if (!iterator.hasNext()) {
                        return false;
                }
                User second = iterator.next();

                boolean forward = followRepository.findByFollowerAndFolloweeAndStatus(first, second, Follow.FollowStatus.ACCEPTED).isPresent();
                boolean reverse = followRepository.findByFollowerAndFolloweeAndStatus(second, first, Follow.FollowStatus.ACCEPTED).isPresent();
                return forward && reverse;
        }

        private Room findExistingDirectRoom(User user1, User user2) {
                List<RoomMember> memberships = roomMemberRepository.findByUser(user1);
                Set<Long> requiredMembers = Set.of(user1.getUserPid(), user2.getUserPid());

                for (RoomMember membership : memberships) {
                        Room room = membership.getRoom();
                        if (room == null || room.getRoomType() != Room.RoomType.PRIVATE) {
                                continue;
                        }

                        List<RoomMember> participants = roomMemberRepository.findByRoom(room);
                        Set<Long> participantIds = participants.stream()
                                .map(member -> member.getUser().getUserPid())
                                .collect(Collectors.toSet());

                        if (participantIds.containsAll(requiredMembers) && participantIds.size() == requiredMembers.size()) {
                                return room;
                        }
                }

                return null;
        }

        private void removeRoomWithDependencies(Room room, List<RoomMember> existingMembers) {
                List<RoomMessage> messages = roomMessageRepository.findByRoom(room);
                for (RoomMessage message : messages) {
                        deleteAttachmentFile(message.getFilePath());
                }
                if (!messages.isEmpty()) {
                        roomMessageRepository.deleteAll(messages);
                }

                List<RoomMember> membersToRemove = existingMembers;
                if (membersToRemove == null || membersToRemove.isEmpty()) {
                        membersToRemove = roomMemberRepository.findByRoom(room);
                }
                if (membersToRemove != null && !membersToRemove.isEmpty()) {
                        roomMemberRepository.deleteAll(membersToRemove);
                }

                archiveMatchRequestsForRoom(room);

                roomRepository.delete(room);
                deleteAttachmentDirectory(room.getRoomId());
        }

        private void archiveMatchRequestsForRoom(Room room) {
                List<MatchRequest> relatedRequests = matchRequestRepository.findByRoom(room);
                if (relatedRequests.isEmpty()) {
                        return;
                }

                for (MatchRequest request : relatedRequests) {
                        request.setStatus(MatchRequest.MatchStatus.ARCHIVED);
                        request.setRoom(null);
                        request.setHandshakeKey(null);
                        request.setHandshakeExpiresAt(null);
                        MatchRequestSanitizer.normalizeAgeRange(request);
                }

                matchRequestRepository.saveAll(relatedRequests);
        }

        private void deleteAttachmentFile(String filePath) {
                if (!StringUtils.hasText(filePath)) {
                        return;
                }
                try {
                        Files.deleteIfExists(Paths.get(filePath));
                } catch (IOException e) {
                        log.warn("Failed to delete attachment file {}", filePath, e);
                }
        }

        private void deleteAttachmentDirectory(Long roomId) {
                if (roomId == null) {
                        return;
                }
                Path directory = attachmentBasePath.resolve(String.valueOf(roomId));
                if (!Files.exists(directory)) {
                        return;
                }
                try (Stream<Path> walk = Files.walk(directory)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                        try {
                                                Files.deleteIfExists(path);
                                        } catch (IOException e) {
                                                log.warn("Failed to delete path {}", path, e);
                                        }
                                });
                } catch (IOException e) {
                        log.warn("Failed to remove attachment directory for room {}", roomId, e);
                }
        }

        private RoomMessage.ContentType determineContentType(String mimeType) {
                if (mimeType != null && mimeType.startsWith("image")) {
                        return RoomMessage.ContentType.IMAGE;
                }
                return RoomMessage.ContentType.FILE;
        }

        private Path ensureUploadDirectory(Long roomId) throws IOException {
                Path roomDir = attachmentBasePath.resolve(String.valueOf(roomId));
                if (!Files.exists(roomDir)) {
                        Files.createDirectories(roomDir);
                }
                return roomDir;
        }

        public record AttachmentResource(Resource resource, String fileName, String mimeType) {}

        public record RoomCleanupResult(boolean deleted, boolean temporary, boolean mutualFollow, String reason) {}

    // TODO: 추가 필요한 메서드들
    // public void joinRoom(Long roomId, String loginId) { }
    // public void leaveRoom(Long roomId, String loginId) { }
    // public List<RoomResponseDto> getUserRooms(String loginId) { }
    // public List<ChatMessageResponseDto> getMessageHistory(Long roomId, int page, int size) { }
    // public void deleteMessage(Long messageId, String loginId) { }
    // public void updateMessage(Long messageId, String newContent, String loginId) { }
    // public void uploadFile(Long roomId, MultipartFile file, String loginId) { }
    // public void markMessageAsRead(Long messageId, String loginId) { }
    // public int getUnreadMessageCount(String loginId) { }
    // public void kickMember(Long roomId, String targetLoginId, String adminLoginId) { }
    // public void updateRoomSettings(Long roomId, RoomSettingsDto settings, String loginId) { }
}