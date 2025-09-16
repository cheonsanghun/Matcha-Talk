package net.datasa.project01.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessagePageResponseDto;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    @Value("${chat.upload-dir:uploads/chat}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    void initUploadDir() {
        try {
            uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new IllegalStateException("채팅 파일 저장 디렉터리를 생성할 수 없습니다.", e);
        }
    }

    public Room createGroupRoom(String loginId) {
        User creator = getUser(loginId);

        Room newRoom = Room.builder()
                .roomType(Room.RoomType.GROUP)
                .capacity(4)
                .build();
        roomRepository.save(newRoom);

        RoomMember newMember = RoomMember.builder()
                .room(newRoom)
                .user(creator)
                .role("HOST")
                .build();
        roomMemberRepository.save(newMember);

        log.info("Group room {} created by {}", newRoom.getRoomId(), loginId);
        return newRoom;
    }

    public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
        User sender = getUser(loginId);
        Room room = getRoom(requestDto.getRoomId());
        ensureActiveMember(room, sender);

        RoomMessage.ContentType contentType = parseContentType(requestDto.getContentType());
        if (contentType == RoomMessage.ContentType.TEXT && !StringUtils.hasText(requestDto.getContent())) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }

        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(contentType)
                .textContent(contentType == RoomMessage.ContentType.TEXT ? requestDto.getContent() : null)
                .fileName(requestDto.getFileName())
                .filePath(requestDto.getFileUrl())
                .mimeType(requestDto.getMimeType())
                .sizeBytes(requestDto.getSizeBytes())
                .build();

        roomMessageRepository.save(message);
        return ChatMessageResponseDto.from(message);
    }

    public ChatMessageResponseDto saveFileMessage(Long roomId, MultipartFile file, String loginId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 필요합니다.");
        }

        User sender = getUser(loginId);
        Room room = getRoom(roomId);
        ensureActiveMember(room, sender);

        try {
            Path roomDir = uploadPath.resolve(String.valueOf(roomId));
            Files.createDirectories(roomDir);

            String originalFileName = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
            String timeStamp = String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            String storedFileName = timeStamp + "_" + originalFileName;

            Path storedPath = roomDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "/uploads/chat/" + roomId + "/" + storedFileName;

            RoomMessage.ContentType type = isImage(file.getContentType())
                    ? RoomMessage.ContentType.IMAGE
                    : RoomMessage.ContentType.FILE;

            RoomMessage message = RoomMessage.builder()
                    .room(room)
                    .sender(sender)
                    .contentType(type)
                    .fileName(originalFileName)
                    .filePath(publicUrl)
                    .mimeType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .build();

            roomMessageRepository.save(message);

            ChatMessageResponseDto response = ChatMessageResponseDto.from(message);
            broadcast(response);
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("파일을 저장하는 중 오류가 발생했습니다.", e);
        }
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponseDto getMessages(Long roomId, String loginId, int page, int size) {
        Room room = getRoom(roomId);
        User user = getUser(loginId);
        ensureActiveMember(room, user);

        Page<RoomMessage> messages = roomMessageRepository.findByRoomOrderByCreatedAtDesc(
                room,
                PageRequest.of(page, size)
        );

        List<ChatMessageResponseDto> responses = new ArrayList<>();
        messages.forEach(message -> responses.add(ChatMessageResponseDto.from(message)));
        responses.sort((a, b) -> a.getSentAt().compareTo(b.getSentAt()));

        return new ChatMessagePageResponseDto(responses, messages.hasNext());
    }

    public Room getOrCreatePrivateRoom(String loginId, String targetLoginId) {
        User me = getUser(loginId);
        User partner = getUser(targetLoginId);

        return roomMemberRepository.findActiveSharedRoom(me, partner, Room.RoomType.PRIVATE)
                .orElseGet(() -> createPrivateRoom(me, partner));
    }

    public void inviteMembers(Long roomId, String inviterLoginId, List<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        if (targets.size() > 2) {
            throw new IllegalArgumentException("최대 2명까지 초대할 수 있습니다.");
        }

        Room room = getRoom(roomId);
        if (room.getRoomType() != Room.RoomType.GROUP) {
            throw new IllegalArgumentException("그룹 방에서만 초대할 수 있습니다.");
        }

        User inviter = getUser(inviterLoginId);
        ensureActiveMember(room, inviter);

        long activeCount = roomMemberRepository.countByRoomAndLeftAtIsNull(room);
        if (activeCount >= room.getCapacity()) {
            throw new IllegalStateException("방 정원을 초과할 수 없습니다.");
        }

        for (String login : targets) {
            if (activeCount >= room.getCapacity()) {
                break;
            }
            User invitee = getUser(login);
            if (roomMemberRepository.existsByRoomAndUserAndLeftAtIsNull(room, invitee)) {
                continue;
            }
            RoomMember member = RoomMember.builder()
                    .room(room)
                    .user(invitee)
                    .role("MEMBER")
                    .invitedByUser(inviter)
                    .build();
            roomMemberRepository.save(member);
            activeCount++;
        }
    }

    private Room createPrivateRoom(User user1, User user2) {
        Room newRoom = Room.builder()
                .roomType(Room.RoomType.PRIVATE)
                .capacity(2)
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

        roomMemberRepository.saveAll(List.of(member1, member2));
        return newRoom;
    }

    private void broadcast(ChatMessageResponseDto responseDto) {
        messagingTemplate.convertAndSend("/topic/rooms/" + responseDto.getRoomId(), responseDto);
    }

    private User getUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private Room getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    private void ensureActiveMember(Room room, User user) {
        if (!roomMemberRepository.existsByRoomAndUserAndLeftAtIsNull(room, user)) {
            throw new IllegalArgumentException("채팅방에 참여하지 않은 사용자입니다.");
        }
    }

    private RoomMessage.ContentType parseContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return RoomMessage.ContentType.TEXT;
        }
        try {
            return RoomMessage.ContentType.valueOf(contentType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RoomMessage.ContentType.TEXT;
        }
    }

    private boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
