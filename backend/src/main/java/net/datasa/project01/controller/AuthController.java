package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.LoginRequest;
import net.datasa.project01.domain.dto.LoginResponse;
import net.datasa.project01.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬(ID/비번) 로그인 전용 컨트롤러.
 *
 * - @RestController: 반환값을 JSON으로 직렬화해 응답한다.
 * - @RequestMapping(..., produces=...): 이 컨트롤러에서 나가는 응답의 Content-Type을 JSON으로 고정한다.
 * - @RequiredArgsConstructor: final 필드(authService)에 대해 생성자를 자동 생성해 DI 받는다.
 *
 * 입력값 검증 흐름:
 * - @RequestBody @Validated LoginRequest req
 *   1) JSON → LoginRequest 바인딩 시점에 Bean Validation(@NotBlank/@Pattern 등)이 수행된다.
 *   2) 실패하면 컨트롤러 메서드가 호출되기 전에 MethodArgumentNotValidException이 발생한다.
 *   3) 해당 예외는 GlobalExceptionHandler가 가로채어 400 JSON으로 변환한다.
 *
 * 비즈니스 실패 흐름:
 * - 서비스가 IllegalArgumentException을 던지면 GlobalExceptionHandler가 400 JSON으로 변환한다.
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

    /** 로그인/잠금 정책을 포함한 실제 인증 로직을 제공하는 서비스 빈 */
    private final AuthService authService;

    /**
     * 로그인 엔드포인트.
     *
     * - consumes=application/json: 본문이 JSON이 아닐 경우 스프링이 415/400을 반환할 수 있다.
     * - @Validated LoginRequest: DTO에 선언된 제약(@NotBlank, @Pattern, @Size 등)을 활성화한다.
     * - 성공 시: UserSummary만 내려 UI가 필요한 최소 정보만 제공한다(민감정보 제외).
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> loginLocal(@RequestBody @Validated LoginRequest req) {
        // 서비스 계층에 실제 인증을 위임한다.
        // 이제 서비스는 UserSummary와 token을 포함한 LoginResponse를 반환합니다.
        LoginResponse response = authService.loginLocal(req.getLoginId(), req.getPassword());
        // 성공 응답: 200 OK + LoginResponse(JSON)
        return ResponseEntity.ok(response);
    }
}
