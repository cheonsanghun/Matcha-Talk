package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.User;

import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.domain.dto.ChatMessageRequestDto; 
import net.datasa.project01.domain.dto.ChatMessageResponseDto; 
import net.datasa.project01.domain.entity.RoomMessage; 

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomMessageRepository roomMessageRepository; // RoomMessageRepository 주입

    /**
     * 새로운 그룹 채팅방을 생성
     * @return 생성된 Room 엔티티
     */
    @Transactional
    public Room createGroupRoom() { 
        // 1. 요청을 보낸 사용자의 정보를 SecurityContextHolder에서 가져오기
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        User creator = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 새로운 Room 엔티티를 생성하고 데이터베이스에 저장
        Room newRoom = Room.builder()
                .roomType(Room.RoomType.GROUP) // Enum 타입 직접 사용
                .capacity(4) // 그룹방의 최대 인원은 4명으로 고정
                .build();
        roomRepository.save(newRoom);

        // 3. 방을 만든 사람을 해당 방의 첫 멤버이자 방장(HOST)으로 추가
        RoomMember newMember = RoomMember.builder()
                .room(newRoom)
                .user(creator)
                .role("HOST") // DB 스키마에 정의된 enum 값
                .build();
        roomMemberRepository.save(newMember);

        return newRoom;
    }
        
    /**
     * 메시지를 처리하고 데이터베이스에 저장한 뒤, 응답 DTO를 생성
     * @param requestDto 클라이언트로부터 받은 메시지 DTO
     * @param loginId 메시지를 보낸 사용자의 로그인 ID
     * @return 다른 클라이언트들에게 브로드캐스팅할 응답 DTO
     */
    @Transactional
    public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
        // 1. 보낸 사람과 채팅방 엔티티를 DB에서 조회합니다.
        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Room room = roomRepository.findById(requestDto.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 2. RoomMessage 엔티티를 생성하고 DB에 저장합니다.
        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build();
        roomMessageRepository.save(message);

        // 3. 다른 클라이언트들에게 전달할 응답 DTO를 생성하여 반환합니다.
        return new ChatMessageResponseDto(
            room.getRoomId(),
            sender.getNickName(),
            message.getTextContent(),
            message.getCreatedAt()
        );
    }
    
    // 향후 여기에 메소드 추가
}