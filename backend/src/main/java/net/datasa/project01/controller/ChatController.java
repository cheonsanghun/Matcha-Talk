package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.service.ChatService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;
    /**
     * 그룹 채팅방 생성 API
     * @param userDetails 방을 생성하는 인증된 사용자 정보
     * @return 생성된 방 정보
     */
    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String loginId = userDetails.getUsername();
            Room createdRoom = chatService.createGroupRoom(loginId); // 생성자 정보를 서비스에 전달
            RoomCreateResponseDto responseDto = RoomCreateResponseDto.fromEntity(createdRoom);
            log.info("Group room created by user '{}' with ID: {}", loginId, createdRoom.getRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (Exception e) {
            log.error("Error creating group room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 현재 로그인된 사용자가 참여중인 모든 채팅방 목록 조회 API
     * @param userDetails 현재 인증된 사용자 정보
     * @return 채팅방 목록
     */
    @GetMapping("/my")
    public ResponseEntity<List<RoomListResponseDto>> getMyRooms(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String loginId = userDetails.getUsername();
            List<RoomListResponseDto> myRooms = chatService.findRoomsByUser(loginId);
            log.info("User '{}' fetched their room list, found {} rooms.", loginId, myRooms.size());
            return ResponseEntity.ok(myRooms);
        } catch (Exception e) {
            log.error("Error fetching rooms for user '{}'", userDetails.getUsername(), e);
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
        try {
            String loginId = userDetails.getUsername();
            // 서비스에서 사용자가 이 방에 참여할 권한이 있는지 확인하는 로직이 필요합니다.
            RoomDetailResponseDto roomDetails = chatService.findRoomDetailsById(roomId, loginId);
            log.info("User '{}' fetched details for room ID: {}", loginId, roomId);
            return ResponseEntity.ok(roomDetails);
        } catch (IllegalArgumentException e) {
            log.warn("Access denied or not found for room ID: {} by user '{}'", roomId, userDetails.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 혹은 403 Forbidden
        }
    }

    @PostMapping(value = "/{roomId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponseDto> uploadChatFile(
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String loginId = userDetails.getUsername();
            ChatMessageResponseDto response = chatService.storeFileMessage(roomId, file, loginId);
            messagingTemplate.convertAndSend(String.format("/topic/rooms/%d", roomId), response);
            log.info("User '{}' uploaded file '{}' to room {}", loginId, response.getFileName(), roomId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to upload file to room {}: {}", roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Unexpected error while uploading file to room {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{roomId}/files/{messageId}")
    public ResponseEntity<Resource> downloadChatFile(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String loginId = userDetails.getUsername();
            RoomMessage message = chatService.getAuthorizedMessage(roomId, messageId, loginId);
            if (message.getContentType() != RoomMessage.ContentType.FILE
                    && message.getContentType() != RoomMessage.ContentType.IMAGE) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Paths.get(message.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            String mimeType = message.getMimeType() != null
                    ? message.getMimeType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            String filename = message.getFileName() != null
                    ? message.getFileName()
                    : resource.getFilename();

            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType(mimeType))
                    .contentLength(message.getSizeBytes() != null
                            ? message.getSizeBytes()
                            : resource.contentLength())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            log.warn("Unauthorized file access for room {} message {}: {}", roomId, messageId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            log.error("Failed to serve chat file {} for room {}", messageId, roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}