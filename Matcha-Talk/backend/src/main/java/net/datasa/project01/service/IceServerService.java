package net.datasa.project01.service;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.IceServerResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class IceServerService {

    private final String rawUrls;
    private final String username;
    private final String credential;

    public IceServerService(@Value("${webrtc.ice.urls:}") String rawUrls,
                            @Value("${webrtc.ice.username:}") String username,
                            @Value("${webrtc.ice.credential:}") String credential) {
        this.rawUrls = rawUrls;
        this.username = username;
        this.credential = credential;
    }

    public List<IceServerResponse> buildIceServers() {
        List<IceServerResponse> servers = new ArrayList<>();

        if (StringUtils.hasText(rawUrls)) {
            for (String entry : rawUrls.split(",")) {
                String url = entry.trim();
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                IceServerResponse.IceServerResponseBuilder builder = IceServerResponse.builder().urls(url);
                if (isTurnServer(url) && StringUtils.hasText(username) && StringUtils.hasText(credential)) {
                    builder.username(username);
                    builder.credential(credential);
                }
                servers.add(builder.build());
            }
        } else {
            servers.add(IceServerResponse.builder().urls("stun:stun.l.google.com:19302").build());
            servers.add(IceServerResponse.builder().urls("stun:stun1.l.google.com:19302").build());
        }

        return servers;
    }

    private boolean isTurnServer(String url) {
        return url.startsWith("turn:") || url.startsWith("turns:");
    }
}
