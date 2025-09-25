package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandler implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_PREFIX = "UNAUTHORIZED: ";
    
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        log.debug("Processing STOMP message with command: {}", accessor.getCommand());
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateWebSocketConnection(accessor);
        }
        
        return message;
    }
    
    private void authenticateWebSocketConnection(StompHeaderAccessor accessor) {
        String authHeader = resolveAuthorizationHeader(accessor);

        if (!StringUtils.hasText(authHeader)) {
            log.warn("STOMP CONNECT rejected: missing Authorization header. sessionId={}", accessor.getSessionId());
            throw new AccessDeniedException(UNAUTHORIZED_PREFIX + "Authorization header가 누락된 WebSocket 연결입니다.");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("STOMP CONNECT rejected: Authorization header is not Bearer. sessionId={}, header={}",
                    accessor.getSessionId(), authHeader);
            throw new AccessDeniedException(UNAUTHORIZED_PREFIX + "Bearer 타입의 Authorization 헤더만 허용됩니다.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("STOMP CONNECT rejected: empty token after Bearer prefix. sessionId={}", accessor.getSessionId());
            throw new AccessDeniedException(UNAUTHORIZED_PREFIX + "JWT 토큰이 비어 있습니다.");
        }

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("STOMP CONNECT rejected: invalid JWT token. sessionId={}", accessor.getSessionId());
                throw new AccessDeniedException(UNAUTHORIZED_PREFIX + "유효하지 않은 JWT 토큰입니다.");
            }

            String loginId = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            log.debug("STOMP CONNECT authenticated. user={}, sessionId={}", loginId, accessor.getSessionId());
        } catch (AccessDeniedException ex) {
            SecurityContextHolder.clearContext();
            throw ex;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.warn("STOMP CONNECT rejected: authentication failure. sessionId={}, cause={}",
                    accessor.getSessionId(), e.getMessage(), e);
            throw new AccessDeniedException(UNAUTHORIZED_PREFIX + "WebSocket 인증에 실패했습니다.", e);
        }
    }

    private String resolveAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header)) {
            return header;
        }

        @SuppressWarnings("unchecked")
        Map<String, List<String>> nativeHeaders = (Map<String, List<String>>) accessor.getHeader(
                NativeMessageHeaderAccessor.NATIVE_HEADERS);

        if (nativeHeaders == null || nativeHeaders.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : nativeHeaders.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (AUTHORIZATION_HEADER.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    continue;
                }
                return values.get(0);
            }
        }

        return null;
    }
}
