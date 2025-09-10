package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom() {
        try {
            Room createdRoom = chatService.createGroupRoom();
            RoomCreateResponseDto responseDto = RoomCreateResponseDto.fromEntity(createdRoom);
            log.info("Group room created successfully with ID: {}", createdRoom.getRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (Exception e) {
            log.error("Error creating group room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}