package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.websocket.RealTimeMessagingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalService {

    private final RealTimeMessagingService messagingService;

    public void relaySignal(String senderLoginId, SignalMessage signalMessage) {
        if (!StringUtils.hasText(signalMessage.getReceiverLoginId())) {
            throw new IllegalArgumentException("수신자 정보가 필요합니다.");
        }

        signalMessage.setSenderLoginId(senderLoginId);
        messagingService.sendEventToUser(signalMessage.getReceiverLoginId(), "signal", signalMessage);
        log.debug("Relayed WebRTC signal of type {} from {} to {}", signalMessage.getType(),
                senderLoginId, signalMessage.getReceiverLoginId());
    }
}
