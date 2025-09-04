package net.datasa.project01.controller;

import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequest;
import net.datasa.project01.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import net.datasa.project01.service.EmailVerificationService;

import java.net.URI;
import java.util.Map;

/**
 * REST 컨트롤러:
 * - POST /api/users/signup : 회원가입
 * - GET  /api/users/{pid}  : 단건 조회(옵션)
 * - GET  /api/users/ping   : 간단 헬스 핑
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerifSvc; // ← 추가


    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "project01");
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody UserSignUpRequest req) {
        UserResponse created = userService.signUp(req);
        // Location 헤더에 생성 리소스 경로 제공(REST 관례)
        return ResponseEntity.created(URI.create("/api/users/" + created.getUserPid()))
                .body(created);
    }

    @GetMapping("/{pid}")
    public ResponseEntity<UserResponse> getOne(@PathVariable Long pid) {
        return ResponseEntity.ok(userService.getUser(pid));
    }


    /** (1) 인증번호 요청 — body: { "email": "..." } */
    @PostMapping("/email/verify/request")
    public Map<String, Object> requestEmailVerify(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        if (email.isBlank()) throw new IllegalArgumentException("이메일을 입력하세요.");
        return emailVerifSvc.requestVerifyEmail(email);
    }


    /** (2) 인증번호 확인 — body: { "token": "123456" } */
    @PostMapping("/email/verify/confirm")
    public Map<String, Object> confirmEmailVerify(@RequestBody Map<String, String> body) {
        String token = body.getOrDefault("token", "").trim();
        if (token.isBlank()) throw new IllegalArgumentException("인증번호를 입력하세요.");
        return emailVerifSvc.confirmVerifyEmail(token);
    }
}
