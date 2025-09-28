package net.datasa.project01.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeMessagingService {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public static final String EVENT_MATCH_FOUND = "match-found";
    public static final String EVENT_MATCH_ROOM_READY = "match-room-ready";
    public static final String EVENT_MATCH_DECLINED = "match-declined";
    public static final String EVENT_MATCH_STATUS = "match-status";

    public void sendEventToUser(String loginId, String event, Object payload) {
        broadcastToUsers(java.util.List.of(loginId), event, payload);
    }

    public void broadcastToUsers(Collection<String> loginIds, String event, Object payload) {
        for (String loginId : loginIds) {
            Collection<WebSocketSession> sessions = sessionRegistry.findSessions(loginId);
            if (sessions.isEmpty()) {
                log.debug("No active WebSocket session for user {}", loginId);
                continue;
            }

            String json;
            try {
                json = objectMapper.writeValueAsString(new SocketEnvelope(event, payload));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize payload for event {}", event, e);
                continue;
            }

            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to send WebSocket message to user {} on session {}", loginId, session.getId(), e);
                }
            }
        }
    }

    private record SocketEnvelope(String event, Object payload) {
    }
}
