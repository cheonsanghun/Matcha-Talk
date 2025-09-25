package net.datasa.project01.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class WebSocketSessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessionsByLoginId = new ConcurrentHashMap<>();

    public void register(String loginId, WebSocketSession session) {
        sessionsByLoginId
                .computeIfAbsent(loginId, key -> new CopyOnWriteArraySet<>())
                .add(session);
        log.debug("Registered WebSocket session {} for user {}", session.getId(), loginId);
    }

    public void remove(String loginId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByLoginId.get(loginId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByLoginId.remove(loginId);
            }
        }
        log.debug("Removed WebSocket session {} for user {}", session.getId(), loginId);
    }

    public Collection<WebSocketSession> findSessions(String loginId) {
        return sessionsByLoginId.getOrDefault(loginId, Set.of());
    }

    public void closeAll(String loginId, CloseStatus status) {
        Collection<WebSocketSession> sessions = findSessions(loginId);
        for (WebSocketSession session : sessions) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("Failed to close session {} for user {}", session.getId(), loginId, e);
            }
        }
    }
}
