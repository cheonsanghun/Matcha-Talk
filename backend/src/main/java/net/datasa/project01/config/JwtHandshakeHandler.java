package net.datasa.project01.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Component
@Slf4j
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    @Nullable
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        Object principal = attributes.get(JwtHandshakeInterceptor.WEBSOCKET_PRINCIPAL_ATTR);

        if (principal instanceof Principal resolvedPrincipal) {
            log.debug("Using principal from handshake attributes: {}", resolvedPrincipal.getName());
            return resolvedPrincipal;
        }

        return super.determineUser(request, wsHandler, attributes);
    }
}
