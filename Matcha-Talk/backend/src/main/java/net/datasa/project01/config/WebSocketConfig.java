package net.datasa.project01.config;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.websocket.SessionHandshakeInterceptor;
import net.datasa.project01.websocket.StompSecurityChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 웹소켓 설정.
 * - /ws 엔드포인트에서 핸드셰이크를 수행하고 SockJS 폴백을 제공한다.
 * - /app 프리픽스로 들어온 메시지를 서버(@MessageMapping)로 라우팅한다.
 * - /user/queue/** 목적지를 통해 1:1 이벤트를 전송한다.
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SessionHandshakeInterceptor sessionHandshakeInterceptor;
    private final StompSecurityChannelInterceptor stompSecurityChannelInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(SessionHandshakeInterceptor sessionHandshakeInterceptor,
                           StompSecurityChannelInterceptor stompSecurityChannelInterceptor,
                           @Value("${app.cors.allowed-origins:*}") String allowedOriginsProperty) {
        this.sessionHandshakeInterceptor = sessionHandshakeInterceptor;
        this.stompSecurityChannelInterceptor = stompSecurityChannelInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);

        if (this.allowedOrigins.length == 0) {
            this.allowedOrigins = new String[] {"*"};
        }
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(sessionHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompSecurityChannelInterceptor);
    }
}
