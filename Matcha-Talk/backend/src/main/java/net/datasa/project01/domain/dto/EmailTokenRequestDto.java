package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailTokenRequestDto {

    @NotBlank
    @Email
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$", message = "Gmail 주소만 사용 가능합니다.")
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "인증번호 형식이 올바르지 않습니다.")
    private String token;
}
