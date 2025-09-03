package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * 텍스트 채팅 메시지를 처리합니다.
     * 목적지: /app/chat.sendMessage/{roomId}
     */
    @MessageMapping("/chat.sendMessage/{roomId}")
    public ChatMessageResponseDto sendMessage(
            @DestinationVariable Long roomId,
            ChatMessageRequestDto requestDto,
            Principal principal) {
        
        String loginId = principal.getName();
        requestDto.setRoomId(roomId);
        return chatService.processMessage(requestDto, loginId);
    }

    /**
     * WebRTC 시그널링 메시지(offer, answer, ice-candidate)를 중계
     * 목적지: /app/signal
     */
    @MessageMapping("/signal")
    public void handleSignal(SignalMessage signalMessage, Principal principal) {
        // 메시지를 보낸 사람의 ID를 서버에서 관리하는 Principal 객체에서 가져와 설정
        // 이는 클라이언트가 보낸 senderLoginId 값을 신뢰하지 않고, 서버에서 직접 지정하여 보안을 강화
        signalMessage.setSenderLoginId(principal.getName());
        
        // 메시지를 받을 사람(receiverLoginId)의 개인 큐(/user/{username}/queue/signals)로 메시지를 전송
        messagingTemplate.convertAndSendToUser(
            signalMessage.getReceiverLoginId(), // 수신자 loginId
            "/queue/signals", // 수신자가 구독할 개인 큐 주소
            signalMessage // 보낼 메시지
        );
    }
}