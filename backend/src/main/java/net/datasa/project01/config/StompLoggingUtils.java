package net.datasa.project01.config;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StompLoggingUtils {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String UNKNOWN_VALUE = "알 수 없음";
    private static final String CLIENT_INBOUND_FAILURE_TEMPLATE = """
문제가 계속되면 잠시 후 다시 시도해주세요. 오류가 반복되면 아래 안내를 참고해 관리자에게 전달해주세요.
STOMP 메시지가 서버 clientInboundChannel로 전달되지 못했습니다.
감지된 원인: %s
가능한 원인:
• 스레드 풀 또는 작업 큐가 포화 상태입니다.
• 메시지 형식이 잘못되었거나 필수 헤더가 누락되었습니다.
• 서버 측 핸들러(@MessageMapping 등)에서 예외가 발생했습니다.
• STOMP 브로커 또는 연결 설정에 문제가 있습니다.
권장 조치:
• 에러 발생 시점의 서버 로그를 확인하고 필요하면 WebSocket 로그 레벨을 DEBUG로 높여 원인을 파악합니다.
• clientInboundChannel에 사용하는 TaskExecutor의 스레드 및 큐 설정을 조정합니다.
• 클라이언트에서 전송하는 STOMP 프레임과 헤더(Authorization 등)를 검증합니다.
• 브로커 또는 WebSocket 엔드포인트 설정과 인증 정보를 다시 확인합니다.
세부 정보:
• STOMP 명령어: %s
• 세션 ID: %s
• 사용자: %s
• 목적지: %s
• 페이로드 타입: %s
• 채널: %s
• 헤더: %s
""";

    private StompLoggingUtils() {
    }

    static Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> sanitized = new LinkedHashMap<>(headers.size());
        headers.forEach((key, value) -> {
            if (key == null) {
                return;
            }

            if (AUTHORIZATION_HEADER.equalsIgnoreCase(key)) {
                sanitized.put(key, Collections.singletonList("****"));
            } else {
                sanitized.put(key, value);
            }
        });

        return sanitized;
    }

    static String extractUser(Principal principal) {
        return principal != null ? principal.getName() : "anonymous";
    }

    static String buildClientInboundFailureMessage(String reason,
                                                   String command,
                                                   String sessionId,
                                                   String user,
                                                   String destination,
                                                   String payloadType,
                                                   String channel,
                                                   Map<String, List<String>> headers) {
        Map<String, List<String>> sanitizedHeaders = headers != null ? headers : Collections.emptyMap();
        return CLIENT_INBOUND_FAILURE_TEMPLATE.formatted(
                fallback(reason),
                fallback(command),
                fallback(sessionId),
                fallback(user),
                fallback(destination),
                fallback(payloadType),
                fallback(channel),
                sanitizedHeaders.isEmpty() ? "{}" : sanitizedHeaders.toString());
    }

    private static String fallback(String value) {
        return value != null && !value.isBlank() ? value : UNKNOWN_VALUE;
    }
}

