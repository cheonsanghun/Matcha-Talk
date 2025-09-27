package net.datasa.project01.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserPenalty;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.repository.jpa.UserPenaltyRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * STOMP 인바운드 채널 보안 인터셉터.
 * - 인증 여부, 방 참여 여부, 제재 상태, 페이로드 크기를 확인한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompSecurityChannelInterceptor implements ChannelInterceptor {

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024; // 256KB

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserPenaltyRepository userPenaltyRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        Principal principal = accessor.getUser();
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        if (principal == null) {
            throw new AccessDeniedException("인증되지 않은 사용자는 STOMP 연결을 할 수 없습니다.");
        }

        User user = userRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));

        switch (command) {
            case CONNECT -> ensureNotPenalized(user);
            case SEND -> {
                enforcePayloadSize(message);
                ensureNotPenalized(user);
            }
            case SUBSCRIBE -> validateSubscription(accessor, user);
            default -> {
                // 기타 명령은 별도 검증 없음
            }
        }

        return message;
    }

    private void ensureNotPenalized(User user) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserPenalty> activePenalty = userPenaltyRepository.findByUser_UserPidOrderByStartsAtDesc(user.getUserPid()).stream()
                .filter(penalty -> penalty.getEndsAt() == null || penalty.getEndsAt().isAfter(now))
                .filter(penalty -> penalty.getStartsAt() == null || !penalty.getStartsAt().isAfter(now))
                .findFirst();

        if (activePenalty.isPresent()) {
            throw new AccessDeniedException("제재 중인 사용자는 실시간 기능을 이용할 수 없습니다.");
        }
    }

    private void enforcePayloadSize(Message<?> message) {
        Object payload = message.getPayload();
        int size;
        if (payload instanceof byte[] bytes) {
            size = bytes.length;
        } else if (payload instanceof String text) {
            size = text.getBytes(StandardCharsets.UTF_8).length;
        } else {
            size = 0;
        }

        if (size > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("전송 가능한 최대 메시지 크기를 초과했습니다.");
        }
    }

    private void validateSubscription(StompHeaderAccessor accessor, User user) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }

        if (destination.startsWith("/topic/rooms/")) {
            Long roomId = extractRoomId(destination);
            if (roomId != null && !isActiveMember(user, roomId)) {
                throw new AccessDeniedException("채팅방에 가입되지 않은 사용자는 구독할 수 없습니다.");
            }
        }
    }

    private Long extractRoomId(String destination) {
        try {
            String roomIdPart = destination.substring(destination.lastIndexOf('/') + 1);
            return Long.parseLong(roomIdPart);
        } catch (Exception e) {
            log.debug("구독 경로에서 roomId를 추출하지 못했습니다. destination={}", destination);
            return null;
        }
    }

    private boolean isActiveMember(User user, Long roomId) {
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        if (roomOptional.isEmpty()) {
            return false;
        }

        return roomMemberRepository.findByRoomAndUser(roomOptional.get(), user)
                .filter(member -> member.getLeftAt() == null)
                .map(RoomMember::getUser)
                .isPresent();
    }
}
