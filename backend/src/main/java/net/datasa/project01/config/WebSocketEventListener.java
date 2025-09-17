package net.datasa.project01.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket CONNECT: sessionId={}, user={}, headers={}",
                accessor.getSessionId(),
                StompLoggingUtils.extractUser(accessor.getUser()),
                StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket CONNECTED: sessionId={}, user={}, headers={}",
                accessor.getSessionId(),
                StompLoggingUtils.extractUser(accessor.getUser()),
                StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("WebSocket SUBSCRIBE: sessionId={}, user={}, destination={}, headers={}",
                accessor.getSessionId(),
                StompLoggingUtils.extractUser(accessor.getUser()),
                accessor.getDestination(),
                StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        CloseStatus closeStatus = event.getCloseStatus();
        String reason = closeStatus != null ? closeStatus.getReason() : null;
        log.info("WebSocket DISCONNECT: sessionId={}, user={}, closeCode={}, reason={}, headers={}",
                accessor.getSessionId(),
                StompLoggingUtils.extractUser(accessor.getUser()),
                closeStatus != null ? closeStatus.getCode() : null,
                reason,
                StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
    }
}

