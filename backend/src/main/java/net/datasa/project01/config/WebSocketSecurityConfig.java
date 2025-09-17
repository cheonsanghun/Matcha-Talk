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
            .simpDestMatchers("/app/**").authenticated()
            .simpDestMatchers("/topic/**", "/queue/**").authenticated()
                .simpSubscribeDestMatchers("/user/queue/**", "/user/topic/**").authenticated()
            .simpTypeMatchers(
                SimpMessageType.CONNECT, 
                SimpMessageType.HEARTBEAT, 
                SimpMessageType.UNSUBSCRIBE, 
                SimpMessageType.DISCONNECT
            ).permitAll()
            .anyMessage().denyAll();

        return messages.build();
    }
}