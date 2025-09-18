package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.datasa.project01.domain.entity.Room.RoomType;

import net.datasa.project01.service.TranslationService;


@Service
@RequiredArgsConstructor
public class ChatService {

        private final RoomRepository roomRepository;
        private final RoomMemberRepository roomMemberRepository;
        private final UserRepository userRepository;
        private final RoomMessageRepository roomMessageRepository;
        private final TranslationService translationService;

        // TODO: 알림 서비스 추가 (Push Notification)
        // private final NotificationService notificationService;
        // TODO: 파일 업로드 서비스 추가
        // private final FileUploadService fileUploadService;
        // TODO: 커비너 세션 및 캐시 관리
        // private final RedisTemplate<String, Object> redisTemplate;

        @Transactional
        public Room createGroupRoom(String loginId) {
                // TODO: 방 이름 설정 기능 추가
                // TODO: 비밀번호 보호 기능 추가
                // TODO: 초대 전용 방 기능 추가
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
                User sender = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                Room room = roomRepository.findById(requestDto.getRoomId())
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                // 1. 원본 메시지를 DB에 저장
                RoomMessage message = RoomMessage.builder()
                        .room(room)
                        .sender(sender)
                        .contentType(RoomMessage.ContentType.TEXT)
                        .textContent(requestDto.getContent())
                        .build();
                roomMessageRepository.save(message);

        // 2. [수정] 번역은 클라이언트에게 위임. 서버는 원본 메시지와 발신자 언어 코드만 전달
        return ChatMessageResponseDto.builder()
                .roomId(room.getRoomId())
                .senderNickName(sender.getNickName())
                .senderLanguageCode(sender.getLanguageCode()) // 발신자 언어 코드 추가
                .content(message.getTextContent())
                // .translatedContent(null) // 이 필드는 이제 존재하지 않으므로 제거합니다.
                .sentAt(message.getCreatedAt())
                .build();
        }
        @Transactional
        public Room createPrivateRoom(User user1, User user2) {
                // 1. 새로운 PRIVATE 타입의 방 생성
                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.PRIVATE)
                        .capacity(2)
                        .build();
                roomRepository.save(newRoom);

                // 2. 두 명의 사용자를 멤버로 추가
                RoomMember member1 = RoomMember.builder()
                        .room(newRoom)
                        .user(user1)
                        .role("MEMBER")
                        .build();
                
                RoomMember member2 = RoomMember.builder()
                        .room(newRoom)
                        .user(user2)
                        .role("MEMBER")
                        .build();
                
                roomMemberRepository.saveAll(java.util.List.of(member1, member2)); // 두 멤버를 한 번에 저장

                return newRoom;
        }

        @Transactional(readOnly = true)
        public List<RoomListResponseDto> findRoomsByUser(String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                // 1. N+1 문제를 해결하기 위해 fetch join으로 사용자가 속한 모든 방과 멤버 정보를 한 번에 조회
                List<RoomMember> allMembersInMyRooms = roomMemberRepository.findAllRoomsAndMembersByUser(user);

                // 2. 조회된 멤버 목록을 '방(Room)' 기준으로 그룹핑
                Map<Room, List<RoomMember>> roomsGroupedByRoom = allMembersInMyRooms.stream()
                        .collect(Collectors.groupingBy(RoomMember::getRoom));

                // 3. 그룹핑된 데이터를 DTO로 변환
                return roomsGroupedByRoom.entrySet().stream()
                        .map(entry -> RoomListResponseDto.fromEntity(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public RoomDetailResponseDto findRoomDetailsById(Long roomId, String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                // 사용자가 해당 방의 멤버인지 확인 (권한 검사)
                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("해당 채팅방에 접근할 권한이 없습니다."));

                // 방의 모든 멤버를 조회하여 상세 DTO 생성
                List<RoomMember> members = roomMemberRepository.findByRoom(room);

                return RoomDetailResponseDto.fromEntity(room, members);
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