package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatFileResource;
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
import net.datasa.project01.service.TranslationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final TranslationService translationService;

    @Value("${chat.storage-dir:uploads/chat}")
    private String chatStorageDir;

    // TODO: 알림 서비스 추가 (Push Notification)
    // private final NotificationService notificationService;
    // TODO: 파일 업로드 서비스 추가
    // private final FileUploadService fileUploadService;
    // TODO: 커비너 세션 및 캐시 관리
    // private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Room createGroupRoom() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("인증된 사용자만 방을 생성할 수 있습니다.");
        }

        String loginId = authentication.getName();
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

        // 1. 원본 메시지를 DB에 저장
        RoomMessage savedMessage = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build());

        String originalText = savedMessage.getTextContent();
        String sourceLang = sender.getLanguageCode();
        String translatedText = originalText;

        if (originalText != null && !originalText.isBlank() && sourceLang != null) {
            if ("ko".equalsIgnoreCase(sourceLang)) {
                translatedText = translationService.translate(originalText, "ko", "ja");
            } else if ("ja".equalsIgnoreCase(sourceLang)) {
                translatedText = translationService.translate(originalText, "ja", "ko");
            }
        }

        return buildResponse(savedMessage, translatedText);
    }

    @Transactional
    public ChatMessageResponseDto storeFileMessage(Long roomId, MultipartFile multipartFile, String loginId) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
        }

        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        String originalFilename = StringUtils.cleanPath(multipartFile.getOriginalFilename() == null
                ? "file"
                : multipartFile.getOriginalFilename());
        String mimeType = multipartFile.getContentType() != null
                ? multipartFile.getContentType()
                : "application/octet-stream";
        RoomMessage.ContentType contentType = mimeType.toLowerCase().startsWith("image/")
                ? RoomMessage.ContentType.IMAGE
                : RoomMessage.ContentType.FILE;

        Path storedPath = storeFileOnDisk(roomId, multipartFile, originalFilename);

        RoomMessage savedMessage = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(contentType)
                .fileName(originalFilename)
                .filePath(buildRelativePath(roomId, storedPath.getFileName().toString()))
                .mimeType(mimeType)
                .sizeBytes(multipartFile.getSize())
                .build());

        return buildResponse(savedMessage, null);
    }

    @Transactional(readOnly = true)
    public ChatFileResource loadFileResource(Long messageId) {
        RoomMessage message = roomMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (message.getFilePath() == null) {
            throw new IllegalArgumentException("파일이 첨부된 메시지가 아닙니다.");
        }

        try {
            Path filePath = Paths.get(chatStorageDir).resolve(message.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("파일을 찾을 수 없거나 읽을 수 없습니다.");
            }
            return new ChatFileResource(resource, message.getFileName(), message.getMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("파일을 불러오는 중 오류가 발생했습니다.", e);
        }
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

    private ChatMessageResponseDto buildResponse(RoomMessage message, String translatedText) {
        LocalDateTime sentAt = message.getCreatedAt();
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }

        String fileUrl = null;
        if (message.getMessageId() != null && message.getFilePath() != null) {
            fileUrl = "/api/chat/files/" + message.getMessageId();
        }

        return ChatMessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom().getRoomId())
                .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                .senderNickName(message.getSender() != null ? message.getSender().getNickName() : null)
                .content(message.getTextContent())
                .translatedContent(translatedText)
                .contentType(message.getContentType() != null ? message.getContentType().name() : null)
                .fileName(message.getFileName())
                .fileUrl(fileUrl)
                .mimeType(message.getMimeType())
                .sizeBytes(message.getSizeBytes())
                .sentAt(sentAt)
                .build();
    }

    private Path storeFileOnDisk(Long roomId, MultipartFile multipartFile, String originalFilename) {
        try {
            Path root = Paths.get(chatStorageDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            Path roomDirectory = root.resolve(String.valueOf(roomId));
            Files.createDirectories(roomDirectory);

            String extension = "";
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot >= 0) {
                extension = originalFilename.substring(lastDot);
            }
            String storedFileName = UUID.randomUUID() + extension;
            Path destination = roomDirectory.resolve(storedFileName);

            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            return destination;
        } catch (IOException e) {
            throw new IllegalStateException("파일을 저장하는 중 오류가 발생했습니다.", e);
        }
    }

    private String buildRelativePath(Long roomId, String storedFileName) {
        return roomId + "/" + storedFileName;
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