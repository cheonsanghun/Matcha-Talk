package net.datasa.project01.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import java.util.Objects;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSecurityConfig.class);

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                .simpTypeMatchers(
                        SimpMessageType.CONNECT,
                        SimpMessageType.HEARTBEAT,
                        SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT
                ).permitAll()
                .simpDestMatchers("/app/**", "/topic/**", "/queue/**", "/user/**").authenticated()
                .simpSubscribeDestMatchers("/topic/**", "/queue/**", "/user/**").authenticated()
                .anyMessage().denyAll();

        AuthorizationManager<Message<?>> delegate = messages.build();
        return (authentication, message) -> {
            AuthorizationDecision decision = delegate.check(authentication, message);

            if (decision == null || !decision.isGranted()) {
                if (log.isWarnEnabled()) {
                    SimpMessageType type = (SimpMessageType) message.getHeaders()
                            .get(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER);
                    String destination = Objects.toString(
                            message.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER),
                            "<none>");
                    log.warn("Denied STOMP frame. type={}, destination={}, user={}",
                            type,
                            destination,
                            authentication != null ? authentication.get().getName() : "anonymous");
                }
            } else if (log.isTraceEnabled()) {
                SimpMessageType type = (SimpMessageType) message.getHeaders()
                        .get(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER);
                String destination = Objects.toString(
                        message.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER),
                        "<none>");
                log.trace("Allowed STOMP frame. type={}, destination={}, user={}",
                        type,
                        destination,
                        authentication != null ? authentication.get().getName() : "anonymous");
            }

            return decision;
        };
    }
}
