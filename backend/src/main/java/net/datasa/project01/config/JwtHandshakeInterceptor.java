package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String WEBSOCKET_PRINCIPAL_ATTR = "WEBSOCKET_PRINCIPAL";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_QUERY_PARAM = "token";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = resolveToken(request);

        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket 핸드셰이크 요청에 JWT가 포함되지 않았습니다. uri={}", request.getURI());
            rejectHandshake(response);
            return false;
        }

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("WebSocket 핸드셰이크에서 JWT 검증에 실패했습니다. uri={}", request.getURI());
                rejectHandshake(response);
                return false;
            }

            String username = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

            attributes.put(WEBSOCKET_PRINCIPAL_ATTR, authentication);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (request instanceof ServletServerHttpRequest servletRequest) {
                servletRequest.getServletRequest()
                        .setAttribute(WEBSOCKET_PRINCIPAL_ATTR, authentication);
            }

            log.info("WebSocket 핸드셰이크 JWT 인증 성공: 사용자={} uri={}", username, request.getURI());

            return true;
        } catch (Exception ex) {
            log.error("WebSocket 핸드셰이크 인증 처리 중 예기치 못한 오류가 발생했습니다. uri={}", request.getURI(), ex);
            rejectHandshake(response);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        SecurityContextHolder.clearContext();
    }

    private String resolveToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String authHeader = headers.getFirst(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        URI uri = request.getURI();
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams();

        String queryToken = queryParams.getFirst(TOKEN_QUERY_PARAM);

        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }

        return null;
    }

    private void rejectHandshake(ServerHttpResponse response) {
        SecurityContextHolder.clearContext();

        if (response == null) {
            return;
        }

        try {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        } catch (Exception ex) {
            log.debug("WebSocket 핸드셰이크 거절 상태 코드를 설정하는 중 문제가 발생했습니다.", ex);
        }
    }
}
