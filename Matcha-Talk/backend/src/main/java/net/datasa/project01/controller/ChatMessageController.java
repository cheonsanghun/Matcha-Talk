package net.datasa.project01.controller;

import java.security.Principal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.service.ChatService;
import net.datasa.project01.websocket.RealTimeMessagingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP 기반 채팅 메시지 처리 컨트롤러.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatMessageController {

    private final ChatService chatService;
    private final RealTimeMessagingService realTimeMessagingService;

    @MessageMapping("/chat/send")
    public void handleChatMessage(ChatMessageRequestDto requestDto, Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        String loginId = principal.getName();
        ChatMessageResponseDto responseDto = chatService.processMessage(requestDto, loginId);
        List<String> participantLoginIds = chatService.findParticipantLoginIds(requestDto.getRoomId());
        realTimeMessagingService.broadcastToUsers(participantLoginIds, "chat", responseDto);
    }
}
