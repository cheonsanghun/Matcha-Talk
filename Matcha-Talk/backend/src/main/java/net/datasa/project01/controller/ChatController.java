package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.DirectChatRequestDto;
import net.datasa.project01.domain.dto.GroupRoomCreateRequestDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.RoomCleanupResponseDto;
import net.datasa.project01.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * 그룹 채팅방 생성 API
     * @param userDetails 방을 생성하는 인증된 사용자 정보
     * @return 생성된 방 정보
     */
    @PostMapping
    public ResponseEntity<RoomDetailResponseDto> createGroupRoom(@AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            RoomDetailResponseDto responseDto = chatService.createGroupRoom(loginId, new GroupRoomCreateRequestDto(null, List.of()));
            log.info("Group room created by user '{}' with ID: {}", loginId, responseDto.getRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid group room create request from '{}': {}", loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating group room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/group")
    public ResponseEntity<RoomDetailResponseDto> createGroupRoomWithMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GroupRoomCreateRequestDto requestDto) {
        String loginId = requireLoginId(userDetails);
        try {
            RoomDetailResponseDto responseDto = chatService.createGroupRoom(loginId, requestDto);
            log.info("Group room created by user '{}' with members {}", loginId, requestDto.memberUserPids());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid group room create request from '{}': {}", loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException e) {
            log.warn("Group room create request rejected for '{}': {}", loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error creating group room with members", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/direct")
    public ResponseEntity<RoomDetailResponseDto> ensureDirectRoom(@AuthenticationPrincipal UserDetails userDetails,
                                                                  @Valid @RequestBody DirectChatRequestDto requestDto) {
        String loginId = requireLoginId(userDetails);
        RoomDetailResponseDto responseDto = chatService.ensureDirectRoom(loginId, requestDto.targetUserPid());
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 현재 로그인된 사용자가 참여중인 모든 채팅방 목록 조회 API
     * @param userDetails 현재 인증된 사용자 정보
     * @return 채팅방 목록
     */
    @GetMapping("/my")
    public ResponseEntity<List<RoomListResponseDto>> getMyRooms(@AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            List<RoomListResponseDto> myRooms = chatService.findRoomsByUser(loginId);
            log.info("User '{}' fetched their room list, found {} rooms.", loginId, myRooms.size());
            return ResponseEntity.ok(myRooms);
        } catch (Exception e) {
            log.error("Error fetching rooms for user '{}'", loginId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 특정 채팅방의 상세 정보 조회 API
     * @param roomId 조회할 방의 ID
     * @param userDetails 현재 인증된 사용자 정보 (권한 확인용)
     * @return 채팅방 상세 정보
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetailResponseDto> getRoomDetails(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            // 서비스에서 사용자가 이 방에 참여할 권한이 있는지 확인하는 로직이 필요합니다.
            RoomDetailResponseDto roomDetails = chatService.findRoomDetailsById(roomId, loginId);
            log.info("User '{}' fetched details for room ID: {}", loginId, roomId);
            return ResponseEntity.ok(roomDetails);
        } catch (IllegalArgumentException e) {
            log.warn("Access denied or not found for room ID: {} by user '{}'", roomId, loginId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 혹은 403 Forbidden
        }
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponseDto>> getRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            List<ChatMessageResponseDto> messages = chatService.getMessageHistory(roomId, loginId, limit);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException e) {
            log.warn("Message history request rejected for room {} by {}: {}", roomId, loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{roomId}/call/ready")
    public ResponseEntity<ChatService.CallReadyResponse> markCallReady(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            return ResponseEntity.ok(chatService.markCallReady(roomId, loginId));
        } catch (IllegalArgumentException e) {
            log.warn("Call ready rejected for room {} by {}: {}", roomId, loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{roomId}/call/hangup")
    public ResponseEntity<ChatService.CallTerminationResponse> terminateCall(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            return ResponseEntity.ok(chatService.endCall(roomId, loginId));
        } catch (IllegalArgumentException e) {
            log.warn("Call termination rejected for room {} by {}: {}", roomId, loginId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{roomId}/attachments")
    public ResponseEntity<ChatMessageResponseDto> uploadAttachment(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            ChatMessageResponseDto responseDto = chatService.saveAttachment(roomId, file, loginId);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException exception) {
            log.warn("Attachment upload rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException exception) {
            log.error("Failed to store attachment for room {}", roomId, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{roomId}/temporary")
    public ResponseEntity<RoomCleanupResponseDto> cleanupTemporaryRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        RoomCleanupResponseDto response = RoomCleanupResponseDto.from(chatService.cleanupTemporaryRoom(roomId, loginId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/attachments/{messageId}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        try {
            ChatService.AttachmentResource attachment = chatService.loadAttachment(roomId, messageId, loginId);

            String fileName = attachment.fileName() != null ? attachment.fileName() : "attachment";
            String contentDisposition = "attachment; filename=\"" + fileName + "\"";
            MediaType mediaType = attachment.mimeType() != null
                    ? MediaType.parseMediaType(attachment.mimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(attachment.resource());
        } catch (IllegalArgumentException exception) {
            log.warn("Attachment download rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException exception) {
            log.error("Failed to read attachment {} in room {}", messageId, roomId, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String requireLoginId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new net.datasa.project01.exception.AuthException(401, "AUTH_REQUIRED: 로그인 후 이용 가능합니다.");
        }
        return userDetails.getUsername();
    }
}
