package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
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

import java.util.List;
import java.util.Map;

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

        if (log.isDebugEnabled()) {
            Map<String, List<String>> headers = StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap());
            log.debug("Processing STOMP message - command={}, sessionId={}, destination={}, user={}, headers={}",
                    accessor.getCommand(),
                    accessor.getSessionId(),
                    accessor.getDestination(),
                    StompLoggingUtils.extractUser(accessor.getUser()),
                    headers);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateWebSocketConnection(accessor);
        }
        
        return message;
    }
    
    private void authenticateWebSocketConnection(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (log.isDebugEnabled()) {
            log.debug("CONNECT frame headers after sanitization: {}",
                    StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
        }

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("No valid authorization header found for WebSocket connection. sessionId={}",
                    accessor.getSessionId());
            return;
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
                log.warn("Invalid JWT token for WebSocket connection. sessionId={}", accessor.getSessionId());
            }
        } catch (Exception e) {
            log.error("Error during WebSocket authentication. sessionId={}", accessor.getSessionId(), e);
        }
    }
}