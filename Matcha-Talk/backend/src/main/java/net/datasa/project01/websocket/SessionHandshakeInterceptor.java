package net.datasa.project01.websocket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * HttpSession 기반 인증 정보를 WebSocket 세션 속성으로 전달하는 인터셉터.
 * - 로그인하지 않은 사용자는 핸드셰이크 단계에서 차단한다.
 * - 인증된 사용자의 loginId를 STOMP 세션 속성에 저장한다.
 */
@Component
@Slf4j
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("세션 정보를 확인할 수 없어 WebSocket 연결을 차단합니다.");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        HttpSession session = httpRequest.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (session == null || authentication == null || !authentication.isAuthenticated()) {
            log.warn("인증 정보가 없어 WebSocket 핸드셰이크를 거부합니다. sessionId={}",
                    session != null ? session.getId() : "none");
            return false;
        }

        String loginId = authentication.getName();
        attributes.put("loginId", loginId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 필요 시 추후 확장
    }
}
