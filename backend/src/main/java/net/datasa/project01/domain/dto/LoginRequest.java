// src/main/java/net/datasa/project01/domain/dto/LoginRequest.java
package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    /**
     * 아이디 또는 이메일 모두 허용
     * - 아이디: 소문자/숫자/밑줄 4~30
     * - 이메일: 일반적인 이메일 패턴
     *
     * JSON 호환성:
     * - 프론트가 현재 보내는 login_id 그대로 받아주도록 @JsonAlias 부여
     *   (snake_case → camelCase 매핑 보장)
     */
    @NotBlank(message = "아이디 또는 이메일을 입력해주세요.")
    @Size(min = 4, max = 100, message = "아이디/이메일 길이가 올바르지 않습니다.")
    @JsonAlias({"login_id", "loginId", "identifier", "email"})
    @Pattern(
            regexp = "(^[a-z0-9_]{4,30}$)|(^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$)",
            message = "아이디 형식(영소문자/숫자/_) 또는 이메일 형식으로 입력해주세요."
    )
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>?,./]).{8,50}$",
            message = "비밀번호는 8자 이상이며 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
    )
    private String password;
}
