package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.PrivateRoomRequestDto;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.service.ChatService;
import net.datasa.project01.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessageSendingOperations messagingTemplate;

    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom() {
        try {
            Room createdRoom = chatService.createGroupRoom();
            RoomCreateResponseDto responseDto = RoomCreateResponseDto.fromEntity(createdRoom);
            log.info("Group room created successfully with ID: {}", createdRoom.getRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalStateException e) {
            log.warn("Unauthorized room creation attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error creating group room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<RoomDetailResponseDto>> getMyRooms(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<RoomDetailResponseDto> rooms = chatService.getRoomsForUser(principal.getName());
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponseDto>> getRecentMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") int size,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ChatMessageResponseDto> messages = chatService.getRecentMessages(roomId, size, principal.getName());
        return ResponseEntity.ok(messages);
    }

    @PostMapping(value = "/{roomId}/files", consumes = {"multipart/form-data"})
    public ResponseEntity<ChatMessageResponseDto> uploadFile(
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ChatMessageResponseDto responseDto = chatService.processFileMessage(roomId, file, principal.getName());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, responseDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PostMapping("/private")
    public ResponseEntity<RoomDetailResponseDto> openPrivateRoom(
            @RequestBody PrivateRoomRequestDto requestDto,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String myLogin = principal.getName();
        String targetLogin = requestDto.getTargetLoginId();
        if (targetLogin == null || targetLogin.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (myLogin.equals(targetLogin)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        User me = userService.getByLoginId(myLogin);
        User target = userService.getByLoginId(targetLogin);
        Room room = chatService.getOrCreatePrivateRoom(me, target);
        RoomDetailResponseDto detail = chatService.getRoomDetail(room.getRoomId(), myLogin);
        return ResponseEntity.ok(detail);
    }
}