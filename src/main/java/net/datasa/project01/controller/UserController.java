package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.UserLoginRequestDto;
import net.datasa.project01.domain.dto.UserProfileResponseDto;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        try {
            userService.signUp(requestDto);
            log.info("User signup successful for loginId: {}", requestDto.getLoginId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("회원가입이 성공적으로 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("User signup failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during signup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody UserLoginRequestDto requestDto) {
        try {
            String token = userService.login(requestDto);
            log.info("User login successful for loginId: {}", requestDto.getLoginId());
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
            log.warn("User login failed for loginId {}: {}", 
                    requestDto.getLoginId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during login for loginId: {}", 
                    requestDto.getLoginId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getProfile() {
        try {
            UserProfileResponseDto userProfile = userService.getMyProfile();
            return ResponseEntity.ok(userProfile);
        } catch (Exception e) {
            log.error("Error retrieving user profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}