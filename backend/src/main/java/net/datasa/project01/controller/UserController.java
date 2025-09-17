package net.datasa.project01.controller;

import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto; // ※ 프로젝트가 UserSignUpRequest(무접미사)라면 여기와 시그니처만 바꿔주세요.
import net.datasa.project01.service.UserService;
import net.datasa.project01.service.EmailVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerifSvc;

    /** 헬스 체크 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "project01");
    }

    /** 회원가입: 201 + Location 헤더 */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody UserSignUpRequestDto req) {
        UserResponse created = userService.signUp(req);
        return ResponseEntity
                .created(URI.create("/api/users/" + created.getUserPid()))
                .body(created);
    }

    /** 단건 조회 */
    @GetMapping("/{pid}")
    public ResponseEntity<UserResponse> getOne(@PathVariable Long pid) {
        return ResponseEntity.ok(userService.getUser(pid));
    }

    /**
     * 중복 확인
     * - ?loginId=foo  또는  ?email=a@b.com
     * - 둘 다 없으면 400
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkExistence(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String email) {

        if (loginId == null && email == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean exists = (loginId != null)
                ? userService.existsByLoginId(loginId)
                : userService.existsByEmail(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /** 현재 로그인 사용자 프로필 */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserResponse userProfile = userService.getUserByLoginId(userDetails.getUsername());
        return ResponseEntity.ok(userProfile);
    }

    /** (1) 이메일 인증번호 요청 — body: { "email": "..." } */
    @PostMapping("/email/verify/request")
    public Map<String, Object> requestEmailVerify(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        if (email.isBlank()) throw new IllegalArgumentException("이메일을 입력하세요.");
        return emailVerifSvc.requestVerifyEmail(email);
    }

    /** (2) 이메일 인증번호 확인 — body: { "email": "...", "token": "123456" } */
    @PostMapping("/email/verify/confirm")
    public Map<String, Object> confirmEmailVerify(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String token = body.getOrDefault("token", "").trim();
        if (email.isBlank() || token.isBlank()) throw new IllegalArgumentException("이메일과 인증번호를 입력하세요.");
        return emailVerifSvc.confirmVerifyEmail(email, token);
    }
}
