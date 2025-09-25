package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.SignalMessage;
import net.datasa.project01.service.SignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webrtc")
@RequiredArgsConstructor
@Slf4j
public class SignalController {

    private final SignalService signalService;

    @PostMapping("/signals")
    public ResponseEntity<Void> relaySignal(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody SignalMessage signalMessage) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        signalService.relaySignal(principal.getUsername(), signalMessage);
        log.trace("Signal relayed via REST endpoint from {} to {}", principal.getUsername(), signalMessage.getReceiverLoginId());
        return ResponseEntity.accepted().build();
    }
}
