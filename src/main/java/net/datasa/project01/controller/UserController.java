package net.datasa.project01.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.UserLoginRequestDto;
import net.datasa.project01.domain.dto.UserProfileResponseDto;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;

    @PostMapping("/signup") // HTTP POST 요청 "/api/users/signup" 경로와 매칭
    public ResponseEntity<String> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        // @RequestBody: HTTP 요청의 본문(body)에 담겨온 JSON 데이터를 UserSignUpRequestDto 객체로 변환
        try {
            userService.signUp(requestDto);
            return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            // 서비스에서 발생한 예외를 잡아 클라이언트에게 400 Bad Request 상태와 함께 에러 메시지를 전달
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequestDto requestDto) {
        try {
            String token = userService.login(requestDto);
            // 로그인 성공 시, 생성된 JWT토큰을 응답 본문에 담아 반환
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
        // 로그인 실패 시(아이디 없음, 비밀번호 틀림), 401 Unauthorized 상태와 함께 에러 메시지를 반환
        return ResponseEntity.status(401).body(e.getMessage());
        }
    }
    /**
     * 현재 인증된 사용자의 프로필 정보를 반환
     * service에서 가공해서 전달해준 DTO를 그대로 클라이언트에게 반환
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getProfile() {
        UserProfileResponseDto userProfile = userService.getMyProfile();
    
        return ResponseEntity.ok(userProfile);
    }
    
}