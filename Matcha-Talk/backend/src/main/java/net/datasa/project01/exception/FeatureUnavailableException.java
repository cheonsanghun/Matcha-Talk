package net.datasa.project01.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 선택 기능이 비활성화된 경우 사용자가 명확한 응답을 받을 수 있도록 하는 예외입니다.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class FeatureUnavailableException extends RuntimeException {

    public FeatureUnavailableException(String message) {
        super(message);
    }
}
