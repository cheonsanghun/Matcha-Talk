package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WebRtcConfigResponse {
    private final List<IceServerResponse> iceServers;
}
