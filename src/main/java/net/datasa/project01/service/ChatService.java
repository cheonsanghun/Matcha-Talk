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
    private final RoomMessageRepository roomMessageRepository;
    // TODO: 알림 서비스 추가 (Push Notification)
    // private final NotificationService notificationService;
    // TODO: 파일 업로드 서비스 추가
    // private final FileUploadService fileUploadService;
    // TODO: 커비너 세션 및 캐시 관리
    // private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Room createGroupRoom() {
        // TODO: 방 이름 설정 기능 추가
        // TODO: 비밀번호 보호 기능 추가
        // TODO: 초대 전용 방 기능 추가 
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

        // TODO: 방 생성 알림 전송
        // TODO: 방 생성 로그 기록
        return newRoom;
    }
        
    @Transactional
    public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
        // TODO: 메시지 내용 필터링 (욕설, 스팸 등)
        // TODO: 메시지 길이 제한 검증
        // TODO: 사용자 참여 권한 확인
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
        // TODO: 오프라인 사용자에게 푸시 알림 전송
        // TODO: 메시지 읽음 여부 추적
        return new ChatMessageResponseDto(
            room.getRoomId(),
            sender.getNickName(),
            message.getTextContent(),
            message.getCreatedAt()
        );
    }
    
    // TODO: 추가 필요한 메서드들
    // public void joinRoom(Long roomId, String loginId) { }
    // public void leaveRoom(Long roomId, String loginId) { }
    // public List<RoomResponseDto> getUserRooms(String loginId) { }
    // public List<ChatMessageResponseDto> getMessageHistory(Long roomId, int page, int size) { }
    // public void deleteMessage(Long messageId, String loginId) { }
    // public void updateMessage(Long messageId, String newContent, String loginId) { }
    // public void uploadFile(Long roomId, MultipartFile file, String loginId) { }
    // public void markMessageAsRead(Long messageId, String loginId) { }
    // public int getUnreadMessageCount(String loginId) { }
    // public void kickMember(Long roomId, String targetLoginId, String adminLoginId) { }
    // public void updateRoomSettings(Long roomId, RoomSettingsDto settings, String loginId) { }
}