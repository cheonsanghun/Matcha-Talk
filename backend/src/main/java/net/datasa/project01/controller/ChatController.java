package net.datasa.project01.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessagePageResponseDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom(
            @AuthenticationPrincipal UserDetails userDetails) {

        Room room = chatService.createGroupRoom(requireUser(userDetails));
        return ResponseEntity.ok(RoomCreateResponseDto.fromEntity(room));
    }

    @PostMapping("/private")
    public ResponseEntity<RoomCreateResponseDto> createPrivateRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid PrivateRoomRequest request) {

        Room room = chatService.getOrCreatePrivateRoom(requireUser(userDetails), request.targetLoginId());
        return ResponseEntity.ok(RoomCreateResponseDto.fromEntity(room));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ChatMessagePageResponseDto> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        return ResponseEntity.ok(chatService.getMessages(roomId, requireUser(userDetails), page, size));
    }

    @PostMapping(value = "/{roomId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponseDto> uploadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file) {

        ChatMessageResponseDto response = chatService.saveFileMessage(roomId, file, requireUser(userDetails));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/invite")
    public ResponseEntity<Void> invite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @RequestBody InviteRequest request) {

        chatService.inviteMembers(roomId, requireUser(userDetails), request.targets());
        return ResponseEntity.ok().build();
    }

    private String requireUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("인증 정보가 필요합니다.");
        }
        return userDetails.getUsername();
    }

    public record PrivateRoomRequest(@NotBlank String targetLoginId) { }

    public record InviteRequest(List<@NotBlank String> targets) { }
}