package net.datasa.project01.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;


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
            logClientInboundFailure(determineExceptionReason(ex), message, channel, accessor, ex);
        } else if (!sent) {
            logClientInboundFailure("메시지가 브로커로 전달되지 않았습니다(sent=false).", message, channel, accessor, null);
        }
    }

    private void logClientInboundFailure(String reason,
                                         Message<?> message,
                                         MessageChannel channel,
                                         StompHeaderAccessor accessor,
                                         Exception ex) {
        String command = accessor != null && accessor.getCommand() != null ? accessor.getCommand().name() : null;
        String sessionId = accessor != null ? accessor.getSessionId() : null;
        String user = accessor != null ? StompLoggingUtils.extractUser(accessor.getUser()) : null;
        String destination = accessor != null ? accessor.getDestination() : null;
        String payloadType = message.getPayload() != null ? message.getPayload().getClass().getSimpleName() : null;
        String channelName = channel != null ? channel.getClass().getSimpleName() : null;
        Map<String, List<String>> headers = accessor != null
                ? StompLoggingUtils.sanitizeHeaders(accessor.toNativeHeaderMap())
                : Collections.emptyMap();

        String diagnosticMessage = StompLoggingUtils.buildClientInboundFailureMessage(
                reason,
                command,
                sessionId,
                user,
                destination,
                payloadType,
                channelName,
                headers
        );

        if (ex != null) {
            log.error(diagnosticMessage, ex);
        } else {
            log.warn(diagnosticMessage);
        }
    }

    private String determineExceptionReason(Exception ex) {
        if (ex instanceof RejectedExecutionException) {
            return "스레드 풀 또는 작업 큐가 포화 상태입니다. (RejectedExecutionException)";
        }
        if (ex instanceof MessageDeliveryException) {
            return "메시지 전달 중 예외 발생(MessageDeliveryException): " + ex.getMessage();
        }
        if (ex instanceof MessagingException) {
            return "메시징 처리 중 예외 발생(" + ex.getClass().getSimpleName() + "): " + ex.getMessage();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();

    }
}

