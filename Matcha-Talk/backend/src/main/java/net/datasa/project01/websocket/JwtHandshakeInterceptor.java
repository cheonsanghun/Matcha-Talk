package net.datasa.project01.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TOKEN_QUERY_PARAM = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("❌ WebSocket handshake rejected: missing token");
            return false;
        }

        if (!jwtUtil.validateToken(token)) {
            log.warn("❌ WebSocket handshake rejected: invalid token");
            return false;
        }

        String loginId = jwtUtil.getUsernameFromToken(token);
        attributes.put("loginId", loginId);
        log.debug("✅ WebSocket handshake authenticated for user: {}", loginId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String authorizationHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
            if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
                return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            }
            String queryToken = httpServletRequest.getParameter(TOKEN_QUERY_PARAM);
            if (StringUtils.hasText(queryToken)) {
                return queryToken.trim();
            }
        }
        return null;
    }
}
