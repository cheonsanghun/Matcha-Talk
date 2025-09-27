package net.datasa.project01.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FindIdRequest;
import net.datasa.project01.domain.dto.FindIdResponse;
import net.datasa.project01.domain.dto.LoginRequest;
import net.datasa.project01.domain.dto.LoginResponse;
import net.datasa.project01.domain.dto.PasswordResetConfirmRequest;
import net.datasa.project01.domain.dto.PasswordResetRequest;
import net.datasa.project01.service.AuthService;
import net.datasa.project01.service.EmailVerificationService;
import net.datasa.project01.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Map;

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
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    /* =====================
     * 로그인
     * ===================== */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> loginLocal(@RequestBody @Validated LoginRequest req,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        LoginResponse loginResponse = authService.loginLocal(req.getLoginId(), req.getPassword());

        if (loginResponse.getUser() != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginResponse.getUser().getLoginId());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            request.getSession(true); // ensure session is created for cookie issuance
            securityContextRepository.saveContext(context, request, response);
        }

        return ResponseEntity.ok(loginResponse);
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

}
