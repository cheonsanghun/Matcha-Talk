package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FindIdRequest;
import net.datasa.project01.domain.dto.FindIdResponse;
import net.datasa.project01.domain.dto.LoginRequest;
import net.datasa.project01.domain.dto.LoginResponse;
import net.datasa.project01.domain.dto.PasswordResetConfirmRequest;
import net.datasa.project01.domain.dto.PasswordResetRequest;
import net.datasa.project01.domain.dto.UserSummary;
import net.datasa.project01.exception.AuthException;
import net.datasa.project01.service.AuthService;
import net.datasa.project01.service.EmailVerificationService;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 인증/계정 관련 REST 엔드포인트.
 * - 로그인, 아이디 찾기, 비밀번호 재설정 요청/확인 제공.
 * - AuthException을 JSON 본문과 함께 상태코드 그대로 전달.
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final UserDetailsServiceImpl userDetailsService;

    /* =====================
     * 로그인
     * ===================== */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> loginLocal(@RequestBody @Validated LoginRequest req,
                                                    HttpServletRequest request) {
        LoginResponse response = authService.loginLocal(req.getLoginId(), req.getPassword());

        UserDetails userDetails = userDetailsService.loadUserByUsername(response.getUser().getLoginId());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        request.changeSessionId();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrfToken(CsrfToken token) {
        if (token == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(Map.of("token", token.getToken()));
    }

    /* =====================
     * 아이디 찾기
     * ===================== */
    @PostMapping(value = "/find-id", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FindIdResponse> findId(@Valid @RequestBody FindIdRequest req) {
        userService.sendLoginIdToEmail(req.getEmail());
        return ResponseEntity.ok(new FindIdResponse("입력하신 이메일로 아이디를 발송했습니다."));
    }

    /* =====================
     * 비밀번호 재설정 - 인증번호 요청
     * ===================== */
    @PostMapping(value = "/password-reset/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        Map<String, Object> result = emailVerificationService.requestResetPassword(req.getEmail());
        return ResponseEntity.ok(result);
    }

    /* =====================
     * 비밀번호 재설정 - 인증번호 검증 + 새 비번 저장
     * ===================== */
    @PostMapping(value = "/password-reset/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest req) {
        Map<String, Object> result = emailVerificationService.confirmResetAndChangePassword(
                req.getEmail(), req.getToken(), req.getNewPassword()
        );
        return ResponseEntity.ok(result);
    }

    /* =====================
     * AuthException → 상태코드 유지 + JSON 응답
     * ===================== */
    private static final Pattern REMAINING_SECONDS  = Pattern.compile("remainingSeconds=(\\d+)");
    private static final Pattern REMAINING_ATTEMPTS = Pattern.compile("remainingAttempts=(\\d+)");

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthException e) {
        final int status = e.getStatus();
        final String raw  = e.getMessage() != null ? e.getMessage() : "";

        final String code =
                raw.startsWith("ACCOUNT_LOCKED")   ? "ACCOUNT_LOCKED" :
                raw.startsWith("BAD_CREDENTIALS")  ? "BAD_CREDENTIALS" : "AUTH_ERROR";

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
