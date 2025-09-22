package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatFileResource;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.service.ChatService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatMessageController {

    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;

    @PostMapping(value = "/rooms/{roomId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponseDto> uploadFile(
            @PathVariable("roomId") Long roomId,
            @RequestPart("file") MultipartFile file,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ChatMessageResponseDto response = chatService.storeFileMessage(roomId, file, principal.getName());
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("파일 업로드 요청이 거부되었습니다: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("채팅 파일 업로드 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/files/{messageId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("messageId") Long messageId) {
        try {
            ChatFileResource fileResource = chatService.loadFileResource(messageId);
            Resource resource = fileResource.resource();
            String filename = fileResource.fileName() != null ? fileResource.fileName() : "attachment";
            String encodedFileName = UriUtils.encode(filename, StandardCharsets.UTF_8);

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (fileResource.mimeType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(fileResource.mimeType());
                } catch (IllegalArgumentException parseException) {
                    log.debug("지원되지 않는 MIME 타입 형식: {}", fileResource.mimeType());
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            log.warn("파일 다운로드 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("파일 다운로드 처리 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
