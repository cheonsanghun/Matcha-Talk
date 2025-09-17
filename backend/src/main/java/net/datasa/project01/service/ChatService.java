package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import net.datasa.project01.domain.entity.Room.RoomType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final TranslationService translationService;

    @Value("${app.chat.upload-dir:uploads/chat}")
    private String chatUploadDir;

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
        if (requestDto.getRoomId() == null) {
            throw new IllegalArgumentException("채팅방 정보가 필요합니다.");
        }
        String content = requestDto.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }

        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(requestDto.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ensureRoomMember(room, sender);

        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(content)
                .build();
        roomMessageRepository.save(message);

        String translatedText = requestDto.isTranslate()
                ? translateMessage(sender, content)
                : null;

        return buildResponse(message, translatedText);
    }

    @Transactional
    public ChatMessageResponseDto storeFileMessage(Long roomId, MultipartFile multipartFile, String loginId) {
        if (roomId == null) {
            throw new IllegalArgumentException("채팅방 정보가 필요합니다.");
        }
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ensureRoomMember(room, sender);

        String originalFilename = multipartFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "file";
        }
        originalFilename = Paths.get(originalFilename).getFileName().toString();

        Path uploadRoot = Paths.get(chatUploadDir == null || chatUploadDir.isBlank()
                ? "uploads/chat"
                : chatUploadDir);
        Path roomDir = uploadRoot.resolve(String.valueOf(roomId));
        try {
            Files.createDirectories(roomDir);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 경로를 생성할 수 없습니다.", e);
        }

        String storedName = UUID.randomUUID() + "_" + originalFilename;
        Path targetPath = roomDir.resolve(storedName).toAbsolutePath().normalize();
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 저장하지 못했습니다.", e);
        }

        String mimeType = multipartFile.getContentType();
        RoomMessage.ContentType contentType = (mimeType != null && mimeType.startsWith("image/"))
                ? RoomMessage.ContentType.IMAGE
                : RoomMessage.ContentType.FILE;

        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(contentType)
                .fileName(originalFilename)
                .filePath(targetPath.toString())
                .mimeType(mimeType)
                .sizeBytes(multipartFile.getSize())
                .build();
        roomMessageRepository.save(message);

        return buildResponse(message, null);
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

    @Transactional(readOnly = true)
    public List<RoomListResponseDto> findRoomsByUser(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 사용자가 속한 모든 RoomMember 엔티티를 찾음
        List<RoomMember> roomMembers = roomMemberRepository.findByUser(user);

        // 각 RoomMember에서 Room을 가져와 DTO로 변환
        return roomMembers.stream()
                .map(RoomMember::getRoom)
                .map(room -> {
                    // 각 방의 멤버 목록을 다시 조회하여 DTO 생성
                    List<RoomMember> membersOfRoom = roomMemberRepository.findByRoom(room);
                    return RoomListResponseDto.fromEntity(room, membersOfRoom);
                })
                .collect(Collectors.toList());
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
    public RoomMessage getAuthorizedMessage(Long roomId, Long messageId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ensureRoomMember(room, user);

        RoomMessage message = roomMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        if (!Objects.equals(message.getRoom().getRoomId(), roomId)) {
            throw new IllegalArgumentException("요청한 채팅방에 속하지 않은 메시지입니다.");
        }
        return message;
    }

    private void ensureRoomMember(Room room, User user) {
        roomMemberRepository.findByRoomAndUser(room, user)
                .orElseThrow(() -> new IllegalArgumentException("채팅방에 참여 중인 사용자만 메시지를 보낼 수 있습니다."));
    }

    private String translateMessage(User sender, String originalText) {
        if (originalText == null || originalText.isBlank()) {
            return originalText;
        }
        String sourceLang = sender.getLanguageCode();
        if (sourceLang == null || sourceLang.isBlank()) {
            return originalText;
        }

        if ("ko".equalsIgnoreCase(sourceLang)) {
            return translationService.translate(originalText, "ko", "ja");
        }
        if ("ja".equalsIgnoreCase(sourceLang)) {
            return translationService.translate(originalText, "ja", "ko");
        }
        return originalText;
    }

    private ChatMessageResponseDto buildResponse(RoomMessage message, String translatedContent) {
        String displayContent = message.getContentType() == RoomMessage.ContentType.TEXT
                ? message.getTextContent()
                : message.getFileName();

        String fileUrl = null;
        if (message.getContentType() == RoomMessage.ContentType.FILE
                || message.getContentType() == RoomMessage.ContentType.IMAGE) {
            fileUrl = String.format("/api/rooms/%d/files/%d",
                    message.getRoom().getRoomId(), message.getMessageId());
        }

        return ChatMessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom().getRoomId())
                .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                .senderNickName(message.getSender() != null ? message.getSender().getNickName() : null)
                .contentType(message.getContentType())
                .content(displayContent)
                .translatedContent(translatedContent)
                .fileName(message.getFileName())
                .fileUrl(fileUrl)
                .mimeType(message.getMimeType())
                .fileSize(message.getSizeBytes())
                .sentAt(message.getCreatedAt())
                .build();
    }

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