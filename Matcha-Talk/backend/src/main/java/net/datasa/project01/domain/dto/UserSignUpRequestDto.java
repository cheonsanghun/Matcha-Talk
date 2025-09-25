package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.time.LocalDate;

/**
 * 회원가입 요청 정보를 담는 DTO(Data Transfer Object) 클래스입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserSignUpRequestDto {

    @NotBlank
    @Size(min = 4, max = 30)
    @Pattern(regexp = "^[a-z0-9_]{4,30}$", message = "아이디는 소문자, 숫자, 밑줄만 사용해 4~30자로 입력해주세요.")
    private String loginId;

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
        message = "비밀번호는 8자 이상, 소문자/숫자/특수문자를 각각 1개 이상 포함해야 합니다."
    )
    private String password;

    @NotBlank
    private String confirmPassword;

    @NotBlank
    @Size(max = 30)
    private String nickName;

    @Email
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail.com$", message = "Gmail 주소만 사용 가능합니다.")
    private String email;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    private String countryCode;

    @NotBlank
    @Pattern(regexp = "^[MF]$")
    private String gender;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @NotBlank
    @Pattern(regexp = "^(ko|ja)$", message = "언어 코드는 'ko' 또는 'ja'여야 합니다.")
    private String languageCode; 

        @NotBlank
    private String verificationToken;

    @AssertTrue(message = "비밀번호와 비밀번호 확인이 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        if (password == null || confirmPassword == null) return false;
        return password.equals(confirmPassword);
    }
}

