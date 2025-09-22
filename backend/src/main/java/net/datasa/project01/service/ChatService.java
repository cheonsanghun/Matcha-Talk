package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
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
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final TranslationService translationService;
    private final FileStorageService fileStorageService;

    @Transactional
    public Room createGroupRoom() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("인증된 사용자만 방을 생성할 수 있습니다.");
        }

        String loginId = authentication.getName();
        User creator = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

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

        return newRoom;
    }

    @Transactional
    public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
        if (requestDto.getRoomId() == null) {
            throw new IllegalArgumentException("대상 채팅방 정보가 필요합니다.");
        }
        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(requestDto.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        assertRoomMembership(room.getRoomId(), sender.getUserPid());

        RoomMessage savedMessage = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build());

        String originalText = Objects.requireNonNullElse(savedMessage.getTextContent(), "");
        String sourceLang = sender.getLanguageCode();
        String translatedText = originalText;

        if (!originalText.isBlank() && sourceLang != null) {
            if ("ko".equalsIgnoreCase(sourceLang)) {
                translatedText = translationService.translate(originalText, "ko", "ja");
            } else if ("ja".equalsIgnoreCase(sourceLang)) {
                translatedText = translationService.translate(originalText, "ja", "ko");
            }
        }

        LocalDateTime sentAt = Objects.requireNonNullElseGet(savedMessage.getCreatedAt(), LocalDateTime::now);

        return ChatMessageResponseDto.builder()
                .messageId(savedMessage.getMessageId())
                .roomId(room.getRoomId())
                .contentType(savedMessage.getContentType())
                .senderNickName(sender.getNickName())
                .content(savedMessage.getTextContent())
                .translatedContent(translatedText)
                .sentAt(sentAt)
                .build();
    }

    @Transactional
    public ChatMessageResponseDto processFileMessage(Long roomId, MultipartFile multipartFile, String loginId) {
        if (roomId == null) {
            throw new IllegalArgumentException("대상 채팅방 정보가 필요합니다.");
        }
        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        assertRoomMembership(room.getRoomId(), sender.getUserPid());

        FileStorageService.StoredFile storedFile = fileStorageService.store(multipartFile, "room-" + room.getRoomId());
        RoomMessage.ContentType contentType = determineContentType(storedFile.getContentType());

        RoomMessage savedMessage = roomMessageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(contentType)
                .fileName(storedFile.getOriginalFileName())
                .filePath(storedFile.getRelativePath())
                .mimeType(storedFile.getContentType())
                .sizeBytes(storedFile.getSize())
                .build());

        LocalDateTime sentAt = Objects.requireNonNullElseGet(savedMessage.getCreatedAt(), LocalDateTime::now);

        return ChatMessageResponseDto.builder()
                .messageId(savedMessage.getMessageId())
                .roomId(room.getRoomId())
                .contentType(savedMessage.getContentType())
                .senderNickName(sender.getNickName())
                .fileName(savedMessage.getFileName())
                .fileUrl(buildFileUrl(room.getRoomId(), savedMessage.getMessageId()))
                .mimeType(savedMessage.getMimeType())
                .fileSize(savedMessage.getSizeBytes())
                .sentAt(sentAt)
                .build();
    }

    @Transactional(readOnly = true)
    public FileDownload getFileForDownload(Long roomId, Long messageId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        RoomMessage message = roomMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getRoom().getRoomId().equals(roomId)) {
            throw new IllegalArgumentException("파일이 요청된 방에 존재하지 않습니다.");
        }
        assertRoomMembership(roomId, user.getUserPid());

        if (message.getFilePath() == null) {
            throw new IllegalStateException("파일이 첨부된 메시지가 아닙니다.");
        }

        Resource resource = fileStorageService.loadAsResource(message.getFilePath());
        String fileName = message.getFileName() != null ? message.getFileName() : "download";
        return new FileDownload(resource, fileName, message.getMimeType());
    }

    @Transactional
    public Room createPrivateRoom(User user1, User user2) {
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

        roomMemberRepository.saveAll(java.util.List.of(member1, member2));
        return newRoom;
    }

    private void assertRoomMembership(Long roomId, Long userPid) {
        boolean isMember = roomMemberRepository.existsByRoom_RoomIdAndUser_UserPidAndLeftAtIsNull(roomId, userPid);
        if (!isMember) {
            throw new IllegalStateException("채팅방에 참여 중인 사용자만 메시지를 전송하거나 파일에 접근할 수 있습니다.");
        }
    }

    private RoomMessage.ContentType determineContentType(String mimeType) {
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            return RoomMessage.ContentType.IMAGE;
        }
        return RoomMessage.ContentType.FILE;
    }

    private String buildFileUrl(Long roomId, Long messageId) {
        return String.format("/api/rooms/%d/files/%d", roomId, messageId);
    }

    public record FileDownload(Resource resource, String fileName, String mimeType) {
    }
}
