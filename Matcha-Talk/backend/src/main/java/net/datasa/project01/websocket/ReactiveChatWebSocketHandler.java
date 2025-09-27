package net.datasa.project01.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ChatService chatService;
    private final RealTimeMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String loginId = ensureLoginId(session);
        sessionRegistry.register(loginId, session);
        messagingService.sendEventToUser(loginId, "connected",
                Map.of("connectedAt", Instant.now().toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String loginId = ensureLoginId(session);
        sessionRegistry.remove(loginId, session);
        log.info("Session {} for user {} closed: {}", session.getId(), loginId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String loginId = ensureLoginId(session);
        JsonNode root = objectMapper.readTree(message.getPayload());

        String event = root.path("event").asText("");
        if (StringUtils.hasText(event)) {
            handleSignalMessage(loginId, event, root.path("payload"));
            return;
        }

        String type = root.path("type").asText("");

        switch (type.toUpperCase()) {
            case "CHAT" -> handleChatMessage(loginId, root);
            case "PING" -> messagingService.sendEventToUser(loginId, "pong",
                    Map.of("timestamp", Instant.now().toString()));
            default -> log.warn("Unknown WebSocket message type '{}' from user {}", type, loginId);
        }
    }

    private void handleChatMessage(String loginId, JsonNode root) {
        long roomId = root.path("roomId").asLong();
        String content = root.path("content").asText("");

        if (roomId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 채팅방 ID 입니다.");
        }

        if (content.isBlank()) {
            log.debug("Ignoring empty chat message from user {}", loginId);
            return;
        }

        ChatMessageRequestDto requestDto = new ChatMessageRequestDto();
        requestDto.setRoomId(roomId);
        requestDto.setContent(content);

        ChatMessageResponseDto responseDto = chatService.processMessage(requestDto, loginId);
        messagingService.broadcastToUsers(
                chatService.findParticipantLoginIds(roomId),
                "chat",
                responseDto
        );
    }

    private void handleSignalMessage(String senderLoginId, String event, JsonNode payloadNode) {
        if (!StringUtils.hasText(event)) {
            log.warn("Ignoring signal event without event name from user {}", senderLoginId);
            return;
        }

        if (payloadNode == null || payloadNode.isMissingNode()) {
            log.warn("Signal event '{}' from user {} has no payload", event, senderLoginId);
            return;
        }

        String receiverLoginId = payloadNode.path("receiverLoginId").asText("");
        if (!StringUtils.hasText(receiverLoginId)) {
            log.warn("Signal event '{}' from user {} missing receiverLoginId", event, senderLoginId);
            return;
        }

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("event", event);
        envelope.put("senderLoginId", senderLoginId);
        envelope.put("receiverLoginId", receiverLoginId);

        if (payloadNode.hasNonNull("metadata")) {
            envelope.set("metadata", payloadNode.get("metadata"));
        }

        JsonNode dataNode = payloadNode.path("data");
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            // allow payload without nested data for backward compatibility
            envelope.set("data", payloadNode);
        } else {
            envelope.set("data", dataNode);
        }

        messagingService.sendEventToUser(receiverLoginId, "signal", envelope);
    }

    private String ensureLoginId(WebSocketSession session) {
        Object existing = session.getAttributes().get("loginId");
        if (existing instanceof String existingLoginId && StringUtils.hasText(existingLoginId)) {
            return existingLoginId;
        }

        String loginId = extractLoginIdFromUri(session);
        if (!StringUtils.hasText(loginId)) {
            loginId = "anon-" + UUID.randomUUID();
        }
        session.getAttributes().put("loginId", loginId);
        return loginId;
    }

    private String extractLoginIdFromUri(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromUri(session.getUri())
                    .build()
                    .getQueryParams()
                    .getFirst("loginId");
        } catch (Exception exception) {
            log.debug("Failed to parse loginId from session URI {}", session.getUri(), exception);
            return null;
        }
    }
}
