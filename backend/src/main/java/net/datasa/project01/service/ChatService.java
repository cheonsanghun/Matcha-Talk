package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomParticipantDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final TranslationService translationService;

    @Value("${chat.files.storage-path:uploads/chat}")
    private String storagePath;

    @Value("${chat.files.public-url-prefix:/files/chat}")
    private String fileUrlPrefix;

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
        User sender = findUserByLoginId(loginId);
        Room room = findRoomById(requestDto.getRoomId());
        validateMembership(room, sender);

        RoomMessage savedMessage = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build());

        return toResponseDto(savedMessage, translateIfNeeded(savedMessage.getTextContent(), sender));
    }

    @Transactional
    public ChatMessageResponseDto processFileMessage(Long roomId, MultipartFile file, String loginId) {
        User sender = findUserByLoginId(loginId);
        Room room = findRoomById(roomId);
        validateMembership(room, sender);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        try {
            Path storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);

            String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("upload");
            String extension = "";
            int idx = originalFilename.lastIndexOf('.');
            if (idx > -1) {
                extension = originalFilename.substring(idx);
            }
            String storedName = UUID.randomUUID() + extension;
            Path target = storageRoot.resolve(storedName);
            Files.copy(file.getInputStream(), target);

            String mimeType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
            RoomMessage.ContentType contentType = mimeType.startsWith("image/")
                    ? RoomMessage.ContentType.IMAGE
                    : RoomMessage.ContentType.FILE;

            RoomMessage saved = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                    .room(room)
                    .sender(sender)
                    .contentType(contentType)
                    .fileName(originalFilename)
                    .filePath(storedName)
                    .mimeType(mimeType)
                    .sizeBytes(file.getSize())
                    .build());

            return toResponseDto(saved, null);
        } catch (Exception ex) {
            throw new IllegalStateException("파일 업로드 중 오류가 발생했습니다.", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getRecentMessages(Long roomId, int size, String loginId) {
        Room room = findRoomById(roomId);
        User user = findUserByLoginId(loginId);
        validateMembership(room, user);

        PageRequest pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return roomMessageRepository.findByRoomOrderByCreatedAtDesc(room, pageable)
                .stream()
                .sorted(Comparator.comparing(RoomMessage::getCreatedAt))
                .map(message -> toResponseDto(message, translateIfNeededForViewer(message, user)))
                .collect(Collectors.toList());
    }

    @Transactional
    public Room getOrCreatePrivateRoom(User user1, User user2) {
        Optional<Room> existing = roomRepository.findPrivateRoomByMemberLogins(
                user1.getLoginId(), user2.getLoginId());
        if (existing.isPresent()) {
            return existing.get();
        }
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

    @Transactional(readOnly = true)
    public Optional<Room> findPrivateRoom(String loginIdA, String loginIdB) {
        return roomRepository.findPrivateRoomByMemberLogins(loginIdA, loginIdB);
    }

    @Transactional(readOnly = true)
    public List<RoomMember> getRoomMembers(Room room) {
        return roomMemberRepository.findByRoom(room);
    }

    @Transactional(readOnly = true)
    public List<RoomDetailResponseDto> getRoomsForUser(String loginId) {
        List<RoomMember> memberships = roomMemberRepository.findByUserLoginId(loginId);
        Map<Long, Room> rooms = new LinkedHashMap<>();
        for (RoomMember membership : memberships) {
            Room room = membership.getRoom();
            rooms.putIfAbsent(room.getRoomId(), room);
        }
        return rooms.values().stream()
                .map(this::buildRoomDetail)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomDetailResponseDto getRoomDetail(Long roomId, String loginId) {
        Room room = findRoomById(roomId);
        User user = findUserByLoginId(loginId);
        validateMembership(room, user);
        return buildRoomDetail(room);
    }

    private User findUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private Room findRoomById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    private void validateMembership(Room room, User user) {
        boolean exists = roomMemberRepository.existsByRoomAndUser(room, user);
        if (!exists) {
            throw new IllegalArgumentException("해당 채팅방에 참여 중인 사용자만 이용할 수 있습니다.");
        }
    }

    private ChatMessageResponseDto toResponseDto(RoomMessage message, String translatedText) {
        String url = null;
        if (message.getFilePath() != null) {
            url = buildFileUrl(message.getFilePath());
        }

        return ChatMessageResponseDto.builder()
                .roomId(message.getRoom().getRoomId())
                .messageId(message.getMessageId())
                .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                .senderNickName(message.getSender() != null ? message.getSender().getNickName() : null)
                .contentType(message.getContentType())
                .content(message.getTextContent())
                .translatedContent(translatedText)
                .fileName(message.getFileName())
                .fileUrl(url)
                .mimeType(message.getMimeType())
                .sizeBytes(message.getSizeBytes())
                .sentAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    private RoomDetailResponseDto buildRoomDetail(Room room) {
        List<RoomParticipantDto> participants = getRoomMembers(room).stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
        return RoomDetailResponseDto.of(room, participants);
    }

    private RoomParticipantDto toParticipantDto(RoomMember member) {
        return new RoomParticipantDto(
                member.getUser().getUserPid(),
                member.getUser().getLoginId(),
                member.getUser().getNickName(),
                member.getRole()
        );
    }

    private String translateIfNeeded(String originalText, User sender) {
        if (originalText == null || originalText.isBlank()) {
            return originalText;
        }
        String sourceLang = sender.getLanguageCode();
        if (sourceLang == null) {
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

    private String translateIfNeededForViewer(RoomMessage message, User viewer) {
        if (message.getContentType() != RoomMessage.ContentType.TEXT) {
            return null;
        }
        if (message.getSender() == null || viewer == null) {
            return message.getTextContent();
        }
        if (message.getSender().getLoginId().equals(viewer.getLoginId())) {
            return translateIfNeeded(message.getTextContent(), message.getSender());
        }
        String viewerLang = viewer.getLanguageCode();
        if (viewerLang == null || message.getTextContent() == null) {
            return message.getTextContent();
        }
        if ("ko".equalsIgnoreCase(viewerLang)) {
            return translationService.translate(message.getTextContent(), "ja", "ko");
        }
        if ("ja".equalsIgnoreCase(viewerLang)) {
            return translationService.translate(message.getTextContent(), "ko", "ja");
        }
        return message.getTextContent();
    }

    private String buildFileUrl(String storedFileName) {
        String prefix = fileUrlPrefix;
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + storedFileName;
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