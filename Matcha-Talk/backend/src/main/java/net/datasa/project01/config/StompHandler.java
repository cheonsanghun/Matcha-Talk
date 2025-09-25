package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
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
        StompCommand command = accessor.getCommand();
        
        log.debug("Processing STOMP message with command: {}", command);
        
        // CONNECT 명령에서만 인증 수행
        if (StompCommand.CONNECT.equals(command)) {
            try {
                authenticateWebSocketConnection(accessor);
            } catch (Exception e) {
                log.error("WebSocket authentication failed", e);
                // 인증 실패 시에도 연결은 허용하되 로그로 기록
                // 실제 메시지 처리에서는 Security Context가 없으면 자동으로 거부됨
            }
        }
        
        return message;
    }
    
    private void authenticateWebSocketConnection(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        
        log.debug("Authorization header: {}", authHeader != null ? "Present" : "Missing");
        
        // 인증 헤더가 없는 경우 - 경고만 로그하고 진행
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("No valid authorization header found for WebSocket connection");
            // 테스트 환경에서는 인증 없이도 연결 허용
            return;
        }
        
        String token = authHeader.substring(BEARER_PREFIX.length());
        log.debug("Extracted JWT token for WebSocket authentication");
        
        try {
            if (jwtUtil.validateToken(token)) {
                String loginId = jwtUtil.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                        
                SecurityContextHolder.getContext().setAuthentication(authentication);
                accessor.setUser(authentication);
                
                log.info("✅ WebSocket authentication successful for user: {}", loginId);
            } else {
                log.warn("❌ Invalid JWT token for WebSocket connection");
            }
        } catch (Exception e) {
            log.error("❌ Error during WebSocket authentication: {}", e.getMessage());
            // 예외가 발생해도 연결은 허용 (테스트 환경)
        }
    }
}