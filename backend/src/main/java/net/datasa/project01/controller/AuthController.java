// src/main/java/net/datasa/project01/controller/AuthController.java
package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FindIdRequest;
import net.datasa.project01.domain.dto.FindIdResponse;
import net.datasa.project01.domain.dto.LoginRequest;
import net.datasa.project01.domain.dto.PasswordResetConfirmRequest;
import net.datasa.project01.domain.dto.PasswordResetRequest;
import net.datasa.project01.domain.dto.UserSummary;
import net.datasa.project01.exception.AuthException;
import net.datasa.project01.service.AuthService;
import net.datasa.project01.service.EmailVerificationService;
import net.datasa.project01.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로컬(ID/비번) 로그인 + 아이디찾기 + 비밀번호 재설정 컨트롤러.
 *
 * - @RestController: 반환값을 JSON으로 직렬화해 응답한다.
 * - @RequestMapping(..., produces=...): 이 컨트롤러에서 나가는 응답의 Content-Type을 JSON으로 고정한다.
 * - @RequiredArgsConstructor: final 필드들에 대해 생성자를 자동 생성해 DI 받는다.
 *
 * 입력값 검증 흐름:
 * - @RequestBody @Validated DTO
 *   1) JSON → DTO 바인딩 시점에 Bean Validation이 수행된다.
 *   2) 실패하면 컨트롤러 메서드가 호출되기 전에 MethodArgumentNotValidException이 발생한다.
 *   3) 해당 예외는 GlobalExceptionHandler가 가로채어 400 JSON으로 변환한다.
 *
 * 비즈니스 실패 흐름:
 * - 서비스가 IllegalArgumentException 또는 AuthException을 던지면 GlobalExceptionHandler/아래 핸들러가 JSON으로 변환한다.
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

    /** 로그인/잠금 정책을 포함한 실제 인증 로직 */
    private final AuthService authService;

    /** 사용자 관련 기능(아이디 찾기 등) */
    private final UserService userService;

    /** 이메일 인증/비번 재설정 로직 */
    private final EmailVerificationService emailVerificationService;

    // =========================
    // 로그인
    // =========================
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserSummary> loginLocal(@RequestBody @Validated LoginRequest req) {
        UserSummary user = authService.loginLocal(req.getLoginId(), req.getPassword());
        return ResponseEntity.ok(user);
    }

    // =========================
    // 아이디 찾기
    // =========================
    @PostMapping(value = "/find-id", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FindIdResponse> findId(@Valid @RequestBody FindIdRequest req) {
        userService.sendLoginIdToEmail(req.getEmail());
        return ResponseEntity.ok(new FindIdResponse("입력하신 이메일로 아이디를 발송했습니다."));
    }

    // =========================
    // 비밀번호 재설정 (1) 인증번호 요청
    // =========================
    @PostMapping(value = "/password-reset/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest req
    ) {
        Map<String, Object> result = emailVerificationService.requestResetPassword(req.getEmail());
        return ResponseEntity.ok(result);
    }

    // =========================
    // 비밀번호 재설정 (2) 인증번호 검증 + 새 비번 저장(원샷)
    // =========================
    @PostMapping(value = "/password-reset/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest req
    ) {
        Map<String, Object> result = emailVerificationService.confirmResetAndChangePassword(
                req.getEmail(), req.getToken(), req.getNewPassword()
        );
        return ResponseEntity.ok(result);
    }

    // =========================================================
    // AuthException → 상태코드 그대로 + 일관된 JSON 바디로 변환
    //  - 401 BAD_CREDENTIALS  { code, message, remainingAttempts }
    //  - 423 ACCOUNT_LOCKED   { code, message, remainingSeconds }
    // =========================================================
    private static final Pattern REMAINING_SECONDS  = Pattern.compile("remainingSeconds=(\\d+)");
    private static final Pattern REMAINING_ATTEMPTS = Pattern.compile("remainingAttempts=(\\d+)");

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthException e) {
        final int status = e.getStatus();
        final String raw  = e.getMessage() != null ? e.getMessage() : "";

        final String code =
                raw.startsWith("ACCOUNT_LOCKED")   ? "ACCOUNT_LOCKED" :
                        raw.startsWith("BAD_CREDENTIALS")  ? "BAD_CREDENTIALS" : "AUTH_ERROR";

        // 사용자에게 보여줄 메시지(프리픽스 제거)
        final String message = stripPrefix(raw);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);

        // 메시지에 포함된 key=value 추출 → JSON 필드로 노출
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

    /** "ACCOUNT_LOCKED: ..." / "BAD_CREDENTIALS: ..." 프리픽스 제거 */
    private String stripPrefix(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf(':');
        if (idx >= 0 && idx + 1 < raw.length()) {
            return raw.substring(idx + 1).trim();
        }
        return raw;
    }
}
