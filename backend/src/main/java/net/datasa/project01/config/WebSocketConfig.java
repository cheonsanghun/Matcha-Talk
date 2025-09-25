package net.datasa.project01.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:*",
            "https://localhost:*",
            "http://127.0.0.1:*",
            "https://127.0.0.1:*"
    );

    private final StompHandler stompHandler;
    private final Environment environment;

    @PostConstruct
    public void init() {
        log.info("WebSocketConfig initialized with StompHandler");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> allowedOrigins = resolveAllowedOrigins();
        String[] originPatterns = allowedOrigins.isEmpty()
                ? DEFAULT_ALLOWED_ORIGINS.toArray(String[]::new)
                : allowedOrigins.toArray(String[]::new);

        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/sockjs/1.5.1/sockjs.min.js");

        log.debug("Registered /ws-stomp endpoint with origins: {}", Arrays.toString(originPatterns));
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(webSocketMessageBrokerTaskScheduler())
                .setHeartbeatValue(new long[]{25_000L, 25_000L});
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        log.debug("Configuring client inbound channel with StompHandler interceptor");
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(1000)
                .keepAliveSeconds(60);
        registration.interceptors(stompHandler);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        log.debug("Configuring client outbound channel with custom task executor");
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(1000)
                .keepAliveSeconds(60);
    }

    private List<String> resolveAllowedOrigins() {
        String raw = environment.getProperty("app.ws.allowed-origins", "");
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    @Bean
    public ThreadPoolTaskScheduler webSocketMessageBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
