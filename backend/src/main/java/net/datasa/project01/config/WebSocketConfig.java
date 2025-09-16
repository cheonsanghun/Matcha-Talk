package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;

import java.util.concurrent.RejectedExecutionException;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;
    private final StompInboundLoggingInterceptor stompInboundLoggingInterceptor;

    private static final int INBOUND_CORE_POOL_SIZE = 4;
    private static final int INBOUND_MAX_POOL_SIZE = 16;
    private static final int INBOUND_QUEUE_CAPACITY = 200;
    private static final int INBOUND_KEEP_ALIVE_SECONDS = 60;

    @PostConstruct
    public void init() {
        log.info("WebSocketConfig initialized with StompHandler");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("http://localhost:5173") // 개발용
                .withSockJS();  // ★ SockJS 필수
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        log.debug("Configuring client inbound channel with diagnostic interceptors");
        registration.interceptors(stompInboundLoggingInterceptor, stompHandler);
        ThreadPoolTaskExecutor executor = diagnosticClientInboundExecutor();

        registration.taskExecutor(executor);
        log.info("clientInboundChannel executor in use - corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), INBOUND_QUEUE_CAPACITY);
    }

    @Bean
    public ThreadPoolTaskExecutor diagnosticClientInboundExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ws-inbound-");
        executor.setCorePoolSize(INBOUND_CORE_POOL_SIZE);
        executor.setMaxPoolSize(INBOUND_MAX_POOL_SIZE);
        executor.setQueueCapacity(INBOUND_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(INBOUND_KEEP_ALIVE_SECONDS);
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            int queueSize = threadPoolExecutor.getQueue() != null ? threadPoolExecutor.getQueue().size() : -1;
            String saturationReason = String.format(
                    "스레드 풀 또는 작업 큐가 포화 상태입니다. activeCount=%d, poolSize=%d, queueSize=%d",
                    threadPoolExecutor.getActiveCount(),
                    threadPoolExecutor.getPoolSize(),
                    queueSize);
            String diagnosticMessage = StompLoggingUtils.buildClientInboundFailureMessage(
                    saturationReason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    threadPoolExecutor.getClass().getSimpleName(),
                    Collections.emptyMap());
            log.error(diagnosticMessage);
            throw new RejectedExecutionException(saturationReason);

        });
        executor.initialize();
        log.info("clientInboundChannel executor initialized - corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                INBOUND_CORE_POOL_SIZE, INBOUND_MAX_POOL_SIZE, INBOUND_QUEUE_CAPACITY);
        return executor;
    }
}