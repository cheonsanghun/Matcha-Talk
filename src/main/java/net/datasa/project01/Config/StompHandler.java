package net.datasa.project01.Config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompHandler.class);
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        logger.debug("STOMP Command: {}", accessor.getCommand());

        // STOMP CONNECT 메시지인 경우에만 JWT 인증 처리
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            logger.info("STOMP Connect message received. Starting JWT validation...");

            // Authorization 헤더에서 JWT 토큰 추출
            String jwtToken = accessor.getFirstNativeHeader("Authorization");

            if (jwtToken != null && jwtToken.startsWith("Bearer ")) {
                String token = jwtToken.substring(7);
                logger.info("Authorization header found. Token: {}", token);
                
                // 토큰 유효성 검증
                if (jwtUtil.validateToken(token)) {
                    logger.info("JWT Token is valid.");
                    String loginId = jwtUtil.getUsernameFromToken(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
                    
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    accessor.setUser(authentication);
                    logger.info("User '{}' authenticated successfully for WebSocket session.", loginId);
                } else {
                    logger.warn("JWT Token validation failed!");
                }
            } else {
                logger.warn("No valid Authorization header found in STOMP CONNECT frame.");
            }
        }
        return message;
    }
}