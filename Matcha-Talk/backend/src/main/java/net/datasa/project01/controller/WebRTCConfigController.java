package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.WebRtcConfigResponse;
import net.datasa.project01.service.IceServerService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/webrtc", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class WebRTCConfigController {

    private final IceServerService iceServerService;

    @GetMapping("/config")
    public ResponseEntity<WebRtcConfigResponse> getConfig() {
        WebRtcConfigResponse body = WebRtcConfigResponse.builder()
                .iceServers(iceServerService.buildIceServers())
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
