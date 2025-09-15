package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;

    /**
     * 회원가입 API
     * @param requestDto 회원가입 요청 데이터
     * @return 생성된 사용자 정보
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        UserResponse response = userService.signUp(requestDto);
        // 회원가입 성공 시, HTTP 201 Created 상태와 함께 생성된 사용자 정보 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인 ID 또는 이메일 중복 확인 API
     * @param loginId 확인할 로그인 ID (옵션)
     * @param email 확인할 이메일 (옵션)
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkExistence(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String email) {
        
        boolean exists = false;
        if (loginId != null) {
            exists = userService.existsByLoginId(loginId);
        } else if (email != null) {
            exists = userService.existsByEmail(email);
        } else {
            // 파라미터가 둘 다 없는 경우 잘못된 요청으로 처리
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
}