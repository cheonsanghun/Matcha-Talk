package net.datasa.project01.Config;

import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // STOMP CONNECT 메시지인 경우에만 JWT 인증 처리
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Authorization 헤더에서 JWT 토큰 추출
            String jwtToken = accessor.getFirstNativeHeader("Authorization");

            if (jwtToken != null && jwtToken.startsWith("Bearer ")) {
                String token = jwtToken.substring(7);
                // 토큰 유효성 검증
                if (jwtUtil.validateToken(token)) {
                    // 토큰에서 사용자 ID 추출
                    String loginId = jwtUtil.getUsernameFromToken(token);
                    // 사용자 정보 로드
                    UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
                    
                    // 인증 객체 생성
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    
                    // SecurityContext에 인증 정보 저장 (이것이 핵심)
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // STOMP 세션에 사용자 정보 저장 (메시지 컨트롤러에서 Principal로 주입받기 위함)
                    accessor.setUser(authentication);
                }
            }
        }
        return message;
    }
}