package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {
    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;

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
            if (signalMessage.getReceiverLoginId() == null || 
                signalMessage.getReceiverLoginId().trim().isEmpty()) {
                log.warn("Signal message missing receiver ID from user: {}", principal.getName());
                return;
            }
            
            signalMessage.setSenderLoginId(principal.getName());
            
            messagingTemplate.convertAndSendToUser(
                signalMessage.getReceiverLoginId(),
                "/queue/signals",
                signalMessage
            );
            
            log.debug("WebRTC signal sent from {} to {}", 
                    principal.getName(), signalMessage.getReceiverLoginId());
                    
        } catch (Exception e) {
            log.error("Error handling WebRTC signal from user: {}", principal.getName(), e);
        }
    }
}