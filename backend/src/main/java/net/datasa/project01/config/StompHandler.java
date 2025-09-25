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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandler implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
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

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            Object nativeHeaders = accessor.getMessageHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
            log.warn("No valid authorization header found for WebSocket connection. headers={}, raw={}", nativeHeaders, authHeader);
            throw new IllegalArgumentException("Authorization header가 누락된 WebSocket 연결입니다.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid JWT token for WebSocket connection");
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
            }

            String loginId = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            log.info("WebSocket authentication successful for user: {}", loginId);
        } catch (Exception e) {
            log.error("Error during WebSocket authentication", e);
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("WebSocket 인증에 실패했습니다.", e);
        }
    }

    private String resolveAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null) {
            header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER.toLowerCase());
        }
        if (header == null) {
            header = accessor.getFirstNativeHeader("AUTHORIZATION");
        }
        return header;
    }
}
