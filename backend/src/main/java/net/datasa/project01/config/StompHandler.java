package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuth != null && existingAuth.isAuthenticated()) {
                accessor.setUser(existingAuth);
                log.debug("Using existing authentication for WebSocket user: {}", existingAuth.getName());
                return;
            }

            log.warn("No valid authorization header found for WebSocket connection");
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없어 WebSocket 연결을 종료합니다.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (jwtUtil.validateToken(token)) {
                String loginId = jwtUtil.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                accessor.setUser(authentication);

                log.info("WebSocket authentication successful for user: {}", loginId);
            } else {
                log.warn("Invalid JWT token for WebSocket connection");
                throw new AuthenticationCredentialsNotFoundException("유효하지 않은 토큰입니다.");
            }
        } catch (AuthenticationCredentialsNotFoundException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error during WebSocket authentication", e);
            throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 중 오류가 발생했습니다.", e);
        }
    }
}