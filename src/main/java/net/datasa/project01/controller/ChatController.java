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
@RequestMapping("/api/rooms") // 채팅방 관련 API는 모두 /api/rooms 경로로 시작(주의!)
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 새로운 그룹 채팅방을 생성하는 API
     * @return 생성된 채팅방의 ID를 포함한 응답
     */
    @PostMapping
    public ResponseEntity<RoomCreateResponseDto> createGroupRoom() {
        // ChatService를 호출하여 비즈니스 로직을 수행하고, 생성된 Room 엔티티를 받기
        Room createdRoom = chatService.createGroupRoom();

        // Room 엔티티를 응답용 DTO로 변환
        RoomCreateResponseDto responseDto = RoomCreateResponseDto.fromEntity(createdRoom);

        // HTTP 상태 코드 201 Created와 함께 응답 DTO를 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
}