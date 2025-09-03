package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom() {
        Room createdRoom = chatService.createGroupRoom();
        RoomCreateResponseDto responseDto = RoomCreateResponseDto.fromEntity(createdRoom);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
}