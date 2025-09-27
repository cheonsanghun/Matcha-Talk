package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.EmailRequestDto;
import net.datasa.project01.domain.dto.EmailTokenRequestDto;
import net.datasa.project01.domain.dto.UserProfileUpdateRequest;
import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.service.EmailVerificationService;
import net.datasa.project01.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    /* ==============================
     * 회원가입 & 중복확인
     * ============================== */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        UserResponse response = userService.signUp(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkExistence(
            @RequestParam(value = "loginId", required = false) String loginId,
            @RequestParam(value = "email", required = false) String email) {
        if (loginId == null && email == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean exists = loginId != null
                ? userService.existsByLoginId(loginId)
                : userService.existsByEmail(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /* ==============================
     * 사용자 조회/수정
     * ============================== */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable Long userId,
                                                      @Valid @RequestBody UserProfileUpdateRequest request) {
        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String loginId = requireLoginId(userDetails);
        UserResponse profile = userService.getUserByLoginId(loginId);
        return ResponseEntity.ok(profile);
    }

    /* ==============================
     * 팔로우 목록
     * ============================== */
    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowingList(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getFollowingList(userId));
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowerList(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getFollowerList(userId));
    }

    /* ==============================
     * 이메일 인증
     * ============================== */
    @PostMapping("/email/verify/request")
    public ResponseEntity<Map<String, Object>> requestEmailVerify(@Valid @RequestBody EmailRequestDto dto) {
        return ResponseEntity.ok(emailVerificationService.requestVerifyEmail(dto.getEmail()));
    }

    @PostMapping("/email/verify/confirm")
    public ResponseEntity<Map<String, Object>> confirmEmailVerify(@Valid @RequestBody EmailTokenRequestDto dto) {
        return ResponseEntity.ok(emailVerificationService.confirmVerifyEmail(dto.getEmail(), dto.getToken()));
    }

    private String requireLoginId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new net.datasa.project01.exception.AuthException(401, "AUTH_REQUIRED: 로그인 후 이용 가능합니다.");
        }
        return userDetails.getUsername();
    }
}
