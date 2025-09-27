package net.datasa.project01.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * STOMP 기반 사용자별 이벤트 브로드캐스팅 서비스.
 * - `/user/queue/events` 목적지로 이벤트 이름/페이로드를 묶어 전송한다.
 * - 매칭/채팅/시그널링 등 서버 주도 알림을 한 곳에서 처리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeMessagingService {

    private static final String USER_QUEUE_DESTINATION = "/queue/events";

    private final SimpMessagingTemplate messagingTemplate;

    public void sendEventToUser(String loginId, String event, Object payload) {
        broadcastToUsers(java.util.List.of(loginId), event, payload);
    }

    public void broadcastToUsers(Collection<String> loginIds, String event, Object payload) {
        for (String loginId : loginIds) {
            messagingTemplate.convertAndSendToUser(loginId, USER_QUEUE_DESTINATION, new SocketEnvelope(event, payload));
            log.debug("보낸 이벤트: user={}, event={}", loginId, event);
        }
    }

    private record SocketEnvelope(String event, Object payload) {
    }
}
