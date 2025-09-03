package net.datasa.project01.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 공통 예외 처리:
 * - IllegalArgumentException : 400 + message
 * - @Valid 바인딩 에러        : 400 + 필드별 메시지
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        String msg = (fe != null)
                ? fe.getField() + ": " + fe.getDefaultMessage()
                : "요청이 올바르지 않습니다.";
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }
}
