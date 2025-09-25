package net.datasa.project01.websocket;

import net.datasa.project01.controller.WebSocketChatController;
import net.datasa.project01.domain.dto.ErrorPayload;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 시그널 라우팅 검증을 위한 최소 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class SignalRoutingTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    private WebSocketChatController controller;

    @BeforeEach
    void setUp() {
        controller = new WebSocketChatController(chatService, messagingTemplate, roomMemberRepository);
    }

    @Test
    void sendsSignalToReceiverQueueWhenAuthorized() {
        when(roomMemberRepository.existsActiveRoomBetweenUsers("alice", "bob", Room.RoomType.PRIVATE))
                .thenReturn(true);

        SignalMessage message = new SignalMessage("offer", "alice", "bob", Map.of("sdp", "dummy"));
        Principal principal = () -> "alice";

        controller.handleSignal(message, principal);

        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/signals"), eq(message));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("alice"), eq("/queue/errors"), org.mockito.Mockito.any());
    }

    @Test
    void routesErrorWhenSenderDoesNotMatchPrincipal() {
        SignalMessage message = new SignalMessage("offer", "mallory", "bob", Map.of("sdp", "dummy"));
        Principal principal = () -> "alice";

        controller.handleSignal(message, principal);

        ArgumentCaptor<ErrorPayload> errorCaptor = ArgumentCaptor.forClass(ErrorPayload.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errorCaptor.capture());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("bob"), eq("/queue/signals"), org.mockito.Mockito.any());

        ErrorPayload payload = errorCaptor.getValue();
        assertThat(payload.getCode()).isEqualTo("SIGNAL_SENDER_MISMATCH");
        assertThat(payload.getMessage()).isNotBlank();
        assertThat(payload.getDetails()).containsEntry("expected", "alice");
    }
}
