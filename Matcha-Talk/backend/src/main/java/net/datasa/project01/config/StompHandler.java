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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        try {
            if (StompCommand.CONNECT.equals(command)) {
                authenticateWebSocketConnection(accessor);
            } else if (StompCommand.DISCONNECT.equals(command)) {
                log.debug("Clearing security context on WebSocket disconnect");
                SecurityContextHolder.clearContext();
            } else {
                propagateAuthentication(accessor);
            }
        } catch (AuthenticationCredentialsNotFoundException | BadCredentialsException ex) {
            log.warn("❌ WebSocket authentication rejected: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during STOMP processing", ex);
            SecurityContextHolder.clearContext();
            throw ex;
        }

        return message;
    }

    private void authenticateWebSocketConnection(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        log.debug("Authorization header present: {}", authHeader != null);

        if (!StringUtils.hasText(authHeader)) {
            throw new AuthenticationCredentialsNotFoundException("Authorization header is missing");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Authorization header must start with 'Bearer '");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("JWT token is empty");
        }

        if (!jwtUtil.validateToken(token)) {
            throw new BadCredentialsException("JWT token is invalid or expired");
        }

        String loginId = jwtUtil.getUsernameFromToken(token);
        log.debug("Extracted loginId {} from JWT for WebSocket", loginId);

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            log.info("✅ WebSocket authentication successful for user: {}", loginId);
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("User not found for WebSocket authentication");
        }
    }

    private void propagateAuthentication(StompHeaderAccessor accessor) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null) {
            return;
        }

        if (accessor.getUser() instanceof Authentication authentication) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.trace("Propagated WebSocket authentication for user {}", authentication.getName());
        }
    }
}