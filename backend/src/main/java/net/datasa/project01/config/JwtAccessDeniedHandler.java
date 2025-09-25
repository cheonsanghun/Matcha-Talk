package net.datasa.project01.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom {@link AccessDeniedHandler} that writes a JSON payload when a request is
 * rejected by Spring Security. This allows the frontend SockJS client to inspect
 * the response body/reason phrase and decide whether a token refresh flow should
 * be triggered.
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (response.isCommitted()) {
            log.debug("Response already committed. Unable to write access denied body for path={}", request.getRequestURI());
            return;
        }

        String message = accessDeniedException.getMessage();
        if (!StringUtils.hasText(message)) {
            message = "UNAUTHORIZED: Access denied";
        }

        log.warn("Access denied for path={} - message={}", request.getRequestURI(), message);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Error-Code", "ACCESS_DENIED");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "ACCESS_DENIED");
        body.put("message", message);
        body.put("path", request.getRequestURI());

        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}

