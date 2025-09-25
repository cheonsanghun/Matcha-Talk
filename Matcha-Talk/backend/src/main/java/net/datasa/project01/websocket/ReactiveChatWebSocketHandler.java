package net.datasa.project01.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;

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
        String loginId = getLoginId(session);
        sessionRegistry.register(loginId, session);
        messagingService.sendEventToUser(loginId, "connected",
                Map.of("connectedAt", Instant.now().toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String loginId = getLoginId(session);
        sessionRegistry.remove(loginId, session);
        log.info("Session {} for user {} closed: {}", session.getId(), loginId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String loginId = getLoginId(session);
        JsonNode root = objectMapper.readTree(message.getPayload());
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

    private String getLoginId(WebSocketSession session) {
        Object loginId = session.getAttributes().get("loginId");
        if (loginId == null) {
            throw new IllegalStateException("WebSocket session is missing authenticated loginId");
        }
        return loginId.toString();
    }
}
