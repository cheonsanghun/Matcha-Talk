package net.datasa.project01.config;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StompLoggingUtils {

    private static final String AUTHORIZATION_HEADER = "Authorization";

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
}

