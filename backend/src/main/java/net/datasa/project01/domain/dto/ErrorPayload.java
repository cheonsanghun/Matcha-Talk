package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * STOMP 오류를 사용자별 큐로 전달하기 위한 표준 페이로드.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ErrorPayload {

    @Builder.Default
    private Instant timestamp = Instant.now();
    private String code;
    private String message;
    @Builder.Default
    private Map<String, Object> details = Collections.emptyMap();
}
