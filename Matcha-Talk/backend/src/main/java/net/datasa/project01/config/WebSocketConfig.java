package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.websocket.SessionHandshakeInterceptor;
import net.datasa.project01.websocket.ReactiveChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final ReactiveChatWebSocketHandler chatWebSocketHandler;
    private final SessionHandshakeInterceptor sessionHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wrapLoggingHandler(chatWebSocketHandler), "/ws/chat")
                .addInterceptors(sessionHandshakeInterceptor)
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                                       WebSocketHandler wsHandler,
                                                       Map<String, Object> attributes) {
                        String loginId = (String) attributes.get("loginId");
                        return () -> loginId;
                    }
                })
                .setAllowedOriginPatterns("*");
    }

    private WebSocketHandler wrapLoggingHandler(WebSocketHandler delegate) {
        return new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
                log.info("✅ WebSocket connection established for user: {}", session.getPrincipal() != null ? session.getPrincipal().getName() : "anonymous");
                delegate.afterConnectionEstablished(session);
            }

            @Override
            public void handleMessage(org.springframework.web.socket.WebSocketSession session,
                                      org.springframework.web.socket.WebSocketMessage<?> message) throws Exception {
                delegate.handleMessage(session, message);
            }

            @Override
            public void handleTransportError(org.springframework.web.socket.WebSocketSession session, Throwable exception) throws Exception {
                log.warn("⚠️ WebSocket transport error for user {}: {}",
                        session.getPrincipal() != null ? session.getPrincipal().getName() : "anonymous",
                        exception.getMessage());
                delegate.handleTransportError(session, exception);
            }

            @Override
            public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                                              org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                log.info("🔌 WebSocket connection closed for user {} with status {}",
                        session.getPrincipal() != null ? session.getPrincipal().getName() : "anonymous",
                        closeStatus);
                delegate.afterConnectionClosed(session, closeStatus);
            }

            @Override
            public boolean supportsPartialMessages() {
                return delegate.supportsPartialMessages();
            }
        };
    }
}
