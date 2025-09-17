package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

/**
 * 회원가입 요청 정보를 담는 DTO(Data Transfer Object) 클래스입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpRequestDto {

    @NotBlank
    @Size(max = 30)
    @JsonProperty("loginId")
    private String loginId;

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
        message = "비밀번호는 8자 이상, 소문자/숫자/특수문자를 각각 1개 이상 포함해야 합니다."
    )
    @JsonProperty("password")
    private String password;

    @NotBlank
    @JsonProperty("confirmPassword")
    private String confirmPassword;

    @NotBlank
    @Size(max = 30)
    @JsonProperty("nickName")
    private String nickName;

    @Email
    @NotBlank
    @Size(max = 100)
    // @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$", message = "Gmail 주소만 사용 가능합니다.") // 필요시 주석 해제
    @JsonProperty("email")
    private String email;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    @JsonProperty("countryCode")
    private String countryCode;

    @NotBlank
    @Pattern(regexp = "^[MF]$")
    @JsonProperty("gender")
    private String gender;

    @NotNull
    @JsonProperty("birthDate")
    private LocalDate birthDate;

    @NotBlank
    @Pattern(regexp = "^(ko|ja)$", message = "언어 코드는 'ko' 또는 'ja'여야 합니다.")
    @JsonProperty("languageCode")
    private String languageCode; 

    @NotBlank(message = "이메일 인증 코드를 입력해주세요.")
    @JsonProperty("verificationCode")
    private String verificationCode;

    @AssertTrue(message = "비밀번호와 비밀번호 확인이 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        if (password == null || confirmPassword == null) return false;
        return password.equals(confirmPassword);
    }
}
