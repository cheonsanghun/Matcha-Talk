package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomCreateResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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
}