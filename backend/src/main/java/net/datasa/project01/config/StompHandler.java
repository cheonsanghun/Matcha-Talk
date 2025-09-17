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
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
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

        Principal handshakePrincipal = accessor.getUser();

        if (handshakePrincipal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("STOMP CONNECT 요청이 핸드셰이크 Principal로 인증되었습니다. sessionId={}, user={}",
                    accessor.getSessionId(), authentication.getName());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("CONNECT 프레임 헤더 정보: {}",
                    StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap()));
        }

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            SecurityContextHolder.clearContext();
            log.warn("STOMP CONNECT 요청에서 Authorization 헤더를 찾을 수 없습니다. sessionId={}",
                    accessor.getSessionId());
            throw new AuthenticationCredentialsNotFoundException("STOMP CONNECT 요청에는 Authorization 헤더가 필요합니다.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (!jwtUtil.validateToken(token)) {
                SecurityContextHolder.clearContext();
                log.warn("STOMP CONNECT에서 JWT 토큰이 유효하지 않습니다. sessionId={}", accessor.getSessionId());
                throw new BadCredentialsException("유효하지 않은 JWT 토큰입니다.");
            }

            String loginId = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            log.info("STOMP CONNECT JWT 인증 성공: sessionId={}, user={}",
                    accessor.getSessionId(), loginId);
        } catch (AuthenticationCredentialsNotFoundException | BadCredentialsException authEx) {
            throw authEx;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error("STOMP CONNECT JWT 인증 처리 중 오류가 발생했습니다. sessionId={}",
                    accessor.getSessionId(), e);
            throw new AuthenticationServiceException("STOMP CONNECT 처리 중 내부 오류가 발생했습니다.", e);
        }
    }
}
