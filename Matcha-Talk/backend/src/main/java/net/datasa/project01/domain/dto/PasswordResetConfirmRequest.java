package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmRequest {

    @NotBlank
    @Email
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$", message = "Gmail 주소만 사용 가능합니다.")
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "인증번호 형식이 올바르지 않습니다.")
    private String token;

    @JsonProperty("newPassword")
    @JsonAlias({"password", "new_password"})
    @NotBlank(message = "새 비밀번호를 입력하세요.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>,.?/]).{8,64}$",
            message = "비밀번호는 8~64자이며 영문 대/소문자·숫자·특수문자를 모두 포함해야 합니다."
    )
    private String newPassword;
}
