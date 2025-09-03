package net.datasa.project01.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker // STOMP 메시징을 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private StompHandler stompHandler;

    /**
     * STOMP 엔드포인트를 등록
     * 클라이언트가 웹소켓 서버에 연결하기 위한 진입점
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // "/ws-connect" 경로로 웹소켓 연결을 허용
        // SockJS는 웹소켓을 지원하지 않는 브라우저를 위한 대체 옵션을 제공
        registry.addEndpoint("/ws-connect")
                .setAllowedOriginPatterns("*") // CORS 문제를 해결하기 위해 모든 출처를 허용 (개발 단계)
                .withSockJS();
    }

    /**
     * 메시지 브로커를 설정
     * 메시지를 라우팅하는 방법을 정의
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/app"으로 시작하는 메시지는 메시지 핸들러(@MessageMapping)로 라우팅
        registry.setApplicationDestinationPrefixes("/app");

        // "/topic", "/queue"로 시작하는 목적지를 가진 메시지를 브로커를 통해 클라이언트에게 전달
        // 브로커는 메시지를 구독(subscribe)하는 클라이언트에게 메시지를 브로드캐스팅
        registry.enableSimpleBroker("/topic", "/queue");
    }

    /**
     * 클라이언트로부터 들어오는 메시지를 처리하는 인바운드 채널을 설정
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 우리가 만든 StompHandler를 인터셉터로 등록
        // 클라이언트의 메시지가 컨트롤러에 도달하기 전에 JWT 인증을 수행
        registration.interceptors(stompHandler);
    }
}
