package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.ErrorPayload;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {
    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final RoomMemberRepository roomMemberRepository;

    /**
     * 텍스트 채팅 메시지를 처리
     * 목적지: /app/chat.sendMessage/{roomId}
     */
    @MessageMapping("/chat.sendMessage/{roomId}")
    @SendTo("/topic/rooms/{roomId}")
    public ChatMessageResponseDto sendMessage(
            @DestinationVariable("roomId") Long roomId,
            ChatMessageRequestDto requestDto,
            Principal principal) {

        try {
            log.debug("Processing chat message for roomId: {} from user: {}",
                    roomId, principal.getName());

            String loginId = principal.getName();
            requestDto.setRoomId(roomId);

            ChatMessageResponseDto response = chatService.processMessage(requestDto, loginId);
            log.info("Chat message processed successfully for roomId: {}", roomId);

            return response;
        } catch (Exception e) {
            log.error("Error processing chat message for roomId: {}", roomId, e);
            throw e;
        }
    }

    /**
     * WebRTC 시그널링 메시지(offer, answer, ice-candidate)를 중계
     * 목적지: /app/signal
     */
    @MessageMapping("/signal")
    public void handleSignal(SignalMessage signalMessage, Principal principal) {
        try {
            if (principal == null) {
                log.warn("Rejected signal message due to missing principal");
                return;
            }
            if (signalMessage == null) {
                log.warn("Received null signal message from user: {}", principal.getName());
                notifyUserError(principal.getName(), "SIGNAL_PAYLOAD_EMPTY",
                        "전달된 시그널 데이터가 비어 있습니다.", Map.of("reason", "payload-null"));
                return;
            }

            String authenticatedLoginId = principal.getName();
            String declaredSender = signalMessage.getSenderLoginId();
            String receiverLoginId = signalMessage.getReceiverLoginId();

            if (StringUtils.hasText(declaredSender) && !authenticatedLoginId.equals(declaredSender)) {
                log.warn("Signal sender mismatch. authenticated={}, declared={} session ignored.",
                        authenticatedLoginId, declaredSender);
                notifyUserError(authenticatedLoginId, "SIGNAL_SENDER_MISMATCH",
                        "시그널 발신자 정보가 올바르지 않습니다.",
                        Map.of("expected", authenticatedLoginId, "received", declaredSender));
                return;
            }

            if (!StringUtils.hasText(receiverLoginId)) {
                log.warn("Signal message missing receiver ID from user: {}", authenticatedLoginId);
                notifyUserError(authenticatedLoginId, "SIGNAL_INVALID_RECEIVER",
                        "시그널 수신자 정보가 누락되었습니다.", Map.of("reason", "receiver-missing"));
                return;
            }

            if (authenticatedLoginId.equals(receiverLoginId)) {
                log.warn("Signal receiver is same as sender. user={}", authenticatedLoginId);
                notifyUserError(authenticatedLoginId, "SIGNAL_SELF_TARGET",
                        "자기 자신에게 시그널을 보낼 수 없습니다.", Map.of("receiver", receiverLoginId));
                return;
            }

            signalMessage.setSenderLoginId(authenticatedLoginId);

            if (!roomMemberRepository.existsActiveRoomBetweenUsers(
                    signalMessage.getSenderLoginId(),
                    signalMessage.getReceiverLoginId(),
                    Room.RoomType.PRIVATE)) {
                log.warn("WebRTC signal blocked due to missing active private room between {} and {}",
                        signalMessage.getSenderLoginId(), signalMessage.getReceiverLoginId());
                notifyUserError(authenticatedLoginId, "SIGNAL_NO_ACTIVE_ROOM",
                        "상대방과 활성화된 매칭을 찾을 수 없습니다.",
                        Map.of("receiver", receiverLoginId));
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    signalMessage.getReceiverLoginId(),
                    "/queue/signals",
                    signalMessage
            );

            log.debug("WebRTC signal sent from {} to {}",
                    authenticatedLoginId, signalMessage.getReceiverLoginId());

        } catch (Exception e) {
            log.error("Error handling WebRTC signal from user: {}", principal.getName(), e);
            notifyUserError(principal.getName(), "SIGNAL_INTERNAL_ERROR",
                    "시그널 처리 중 오류가 발생했습니다.", Map.of("error", e.getClass().getSimpleName()));
        }
    }

    private void notifyUserError(String loginId, String code, String message, Map<String, Object> details) {
        if (!StringUtils.hasText(loginId)) {
            log.warn("Cannot send STOMP error without loginId. code={} message={}", code, message);
            return;
        }

        ErrorPayload payload = ErrorPayload.builder()
                .code(code)
                .message(message)
                .details(details != null ? details : Map.of())
                .build();

        messagingTemplate.convertAndSendToUser(loginId, "/queue/errors", payload);
    }
}
