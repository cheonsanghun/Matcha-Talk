package net.datasa.project01.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            // 애플리케이션 메시지는 인증 필요
            .simpDestMatchers("/app/**").authenticated()
            // Topic과 Queue 구독은 인증 필요 (단, 더 관대하게 설정)
            .simpDestMatchers("/topic/**", "/queue/**").permitAll()
            // 연결, 심박, 구독 해제, 연결 해제는 허용
            .simpTypeMatchers(
                SimpMessageType.CONNECT, 
                SimpMessageType.HEARTBEAT, 
                SimpMessageType.UNSUBSCRIBE, 
                SimpMessageType.DISCONNECT,
                SimpMessageType.SUBSCRIBE  // 구독도 허용
            ).permitAll()
            // 나머지는 인증 필요 (denyAll에서 변경)
            .anyMessage().authenticated();

        return messages.build();
    }
}
