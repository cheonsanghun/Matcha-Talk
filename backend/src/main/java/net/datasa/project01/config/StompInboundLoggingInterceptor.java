package net.datasa.project01.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StompInboundLoggingInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            if (log.isDebugEnabled()) {
                Map<String, List<String>> headers = StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap());
                log.debug("Inbound STOMP message: command={}, sessionId={}, user={}, destination={}, payloadType={}, headers={}",
                        accessor.getCommand(),
                        accessor.getSessionId(),
                        StompLoggingUtils.extractUser(accessor.getUser()),
                        accessor.getDestination(),
                        message.getPayload() != null ? message.getPayload().getClass().getSimpleName() : "null",
                        headers);
            }
        } else if (log.isDebugEnabled()) {
            log.debug("Inbound non-STOMP message received on channel {}: payloadType={} payload={}",
                    channel.getClass().getSimpleName(),
                    message.getPayload() != null ? message.getPayload().getClass().getSimpleName() : "null",
                    message.getPayload());
        }
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (ex != null) {
            if (accessor != null) {
                log.error("Error while processing inbound STOMP message: command={}, sessionId={}, user={}, destination={}, reason={}",
                        accessor.getCommand(),
                        accessor.getSessionId(),
                        StompLoggingUtils.extractUser(accessor.getUser()),
                        accessor.getDestination(),
                        ex.getMessage(),
                        ex);
            } else {
                log.error("Error while processing inbound message without STOMP headers: {}", ex.getMessage(), ex);
            }
        } else if (!sent && accessor != null) {
            log.warn("Inbound STOMP message was not forwarded to the broker: command={}, sessionId={}, user={}, destination={}",
                    accessor.getCommand(),
                    accessor.getSessionId(),
                    StompLoggingUtils.extractUser(accessor.getUser()),
                    accessor.getDestination());
        }
    }
}

