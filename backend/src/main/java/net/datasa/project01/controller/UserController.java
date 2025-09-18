package net.datasa.project01.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.service.UserService;
import net.datasa.project01.service.EmailVerificationService;
import net.datasa.project01.util.JwtUtil;
import net.datasa.project01.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import net.datasa.project01.domain.dto.EmailRequestDto;
import net.datasa.project01.domain.dto.EmailTokenRequestDto;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil; // Inject JwtUtil
    private final UserRepository userRepository; // Inject UserRepository

    /**
     * 회원가입 API
     * @param requestDto 회원가입 요청 데이터
     * @return 생성된 사용자 정보
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        log.info("회원가입 요청 받음: {}", requestDto.getLoginId());
        UserResponse response = userService.signUp(requestDto);
        log.info("회원가입 성공: {}", response.getLoginId());
        // 회원가입 성공 시, HTTP 201 Created 상태와 함께 생성된 사용자 정보 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/signup/debug")
    public ResponseEntity<String> debugSignUp(HttpServletRequest request) {
        try {
            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            log.info("Raw /signup/debug request body: {}", body);
            return ResponseEntity.ok("Raw body logged.");
        } catch (IOException e) {
            log.error("Error reading raw request body", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading raw body.");
        }
    }

    /**
     * 로그인 ID 또는 이메일 중복 확인 API
     * @param loginId 확인할 로그인 ID (옵션)
     * @param email 확인할 이메일 (옵션)
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkExistence(
            @RequestParam(value = "loginId", required = false) String loginId,
            @RequestParam(value = "email", required = false) String email) {
        
        log.info("중복 확인 요청 - loginId: {}, email: {}", loginId, email);
        
        boolean exists = false;
        if (loginId != null) {
            exists = userService.existsByLoginId(loginId);
            log.info("loginId '{}' 중복 확인 결과: {}", loginId, exists);
        } else if (email != null) {
            exists = userService.existsByEmail(email);
            log.info("email '{}' 중복 확인 결과: {}", email, exists);
        } else {
            // 파라미터가 둘 다 없는 경우 잘못된 요청으로 처리
            log.warn("중복 확인 요청에 loginId와 email이 모두 없음");
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * 현재 로그인된 사용자의 프로필 정보 조회 API
     * @param userDetails JWT 인증을 통해 얻은 현재 사용자 정보
     * @return 사용자 프로필 응답 DTO
     */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        // userDetails.getUsername()은 현재 로그인된 사용자의 loginId를 반환
        // User 엔티티를 직접 조회하여 DTO로 변환하는 로직이 필요합니다. (UserService에 추가 필요)
        UserResponse userProfile = userService.getUserByLoginId(userDetails.getUsername());
        return ResponseEntity.ok(userProfile);
    }
    
    // 참고: getUser(Long pid)와 같은 다른 메소드들은 필요에 따라 계속 추가할 수 있음

    /**
     * 특정 사용자의 팔로잉 목록 조회 API
     * @param userId 조회할 사용자의 ID
     * @return 팔로잉 목록
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowingList(@PathVariable Long userId) {
        List<UserResponse> followingList = userService.getFollowingList(userId);
        return ResponseEntity.ok(followingList);
    }

    /**
     * 특정 사용자의 팔로워 목록 조회 API
     * @param userId 조회할 사용자의 ID
     * @return 팔로워 목록
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowerList(@PathVariable Long userId) {
        List<UserResponse> followerList = userService.getFollowerList(userId);
        return ResponseEntity.ok(followerList);
    }

    /**
     * 이메일 인증 요청 API
     * @param dto 이메일 주소
     * @return 인증번호 전송 결과
     */
    @PostMapping("/email/verify/request")
    public ResponseEntity<Map<String, Object>> requestEmailVerify(@Valid @RequestBody EmailRequestDto dto) {
        Map<String, Object> response = emailVerificationService.requestVerifyEmail(dto.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * 이메일 인증 확인 API
     * @param dto 이메일 주소와 인증 토큰
     * @return 인증 확인 결과
     */
    @PostMapping("/email/verify/confirm")
    public ResponseEntity<Map<String, Object>> confirmEmailVerify(@Valid @RequestBody EmailTokenRequestDto dto) {
        Map<String, Object> response = emailVerificationService.confirmVerifyEmail(dto.getEmail(), dto.getToken());
        return ResponseEntity.ok(response);
    }

    /**
     * [개발/디버깅용] loginId로 사용자를 조회하는 임시 API
     * @param id 조회할 사용자의 loginId
     * @return 사용자 정보 또는 404 Not Found
     */
    @GetMapping("/find-by-login-id")
    public ResponseEntity<?> findUserByLoginIdForDebug(@RequestParam(value = "id") String id) {
        // loginId로 사용자를 조회합니다.
        return userRepository.findByLoginId(id)
                // map의 결과 타입을 <ResponseEntity<?>>로 명시
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(UserResponse.fromEntity(user)))
                // 사용자가 없을 경우, 다른 타입의 ResponseEntity를 반환
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User with loginId '" + id + "' not found.")));
    }
}
