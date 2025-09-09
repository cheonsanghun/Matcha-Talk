package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 로그인 요청 DTO.
 *
 * 검증 포인트:
 * - 아이디(loginId)
 *   · 공백 금지(@NotBlank)
 *   · 길이 4~30(@Size)
 *   · 허용 문자 집합: 영소문자/숫자/밑줄(@Pattern)
 *
 * - 비밀번호(password)
 *   · 공백 금지(@NotBlank)
 *   · 길이 8~50(@Size)
 *   · 복잡도: 소문자/숫자/특수문자 각각 ≥ 1개(@Pattern)
 *
 * JSON 키 매핑:
 * - 프로젝트 설정에 spring.jackson.property-naming-strategy=SNAKE_CASE가 켜져 있다면
 *   클라이언트가 "login_id"/"password"로 보내도 필드 "loginId"/"password"에 자동 매핑된다.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 30, message = "아이디는 4~30자여야 합니다.")

    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")

    private String password;
}
