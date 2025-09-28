package net.datasa.project01.controller.advice;

import net.datasa.project01.exception.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class AuthExceptionAdvice {

    private static final Pattern REMAINING_SECONDS  = Pattern.compile("remainingSeconds=(\\d+)");
    private static final Pattern REMAINING_ATTEMPTS = Pattern.compile("remainingAttempts=(\\d+)");

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthException e) {
        final int status = e.getStatus();
        final String raw  = e.getMessage() != null ? e.getMessage() : "";

        final String code =
                raw.startsWith("ACCOUNT_LOCKED")   ? "ACCOUNT_LOCKED" :
                raw.startsWith("BAD_CREDENTIALS")  ? "BAD_CREDENTIALS" :
                raw.startsWith("AUTH_REQUIRED")    ? "AUTH_REQUIRED" :
                raw.startsWith("AUTH_ERROR")       ? "AUTH_ERROR" :
                "AUTH_ERROR";

        final String message = stripPrefix(raw);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);

        Matcher ms = REMAINING_SECONDS.matcher(raw);
        if (ms.find()) {
            try { body.put("remainingSeconds", Long.parseLong(ms.group(1))); } catch (NumberFormatException ignore) {}
        }
        Matcher ma = REMAINING_ATTEMPTS.matcher(raw);
        if (ma.find()) {
            try { body.put("remainingAttempts", Integer.parseInt(ma.group(1))); } catch (NumberFormatException ignore) {}
        }

        return ResponseEntity.status(status).body(body);
    }

    private String stripPrefix(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf(':');
        if (idx >= 0 && idx + 1 < raw.length()) {
            return raw.substring(idx + 1).trim();
        }
        return raw;
    }
}
