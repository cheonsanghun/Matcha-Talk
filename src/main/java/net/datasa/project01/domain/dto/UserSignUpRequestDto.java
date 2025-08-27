package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSignUpRequestDto {

    @NotBlank(message = "아이디는 필수 입력 항목입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    private String nickName;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    private String email;

    @NotBlank(message = "국적 코드는 필수 입력 항목입니다.")
    private String countryCode;

    @NotBlank(message = "성별은 필수 입력 항목입니다.")
    @Pattern(regexp = "^[MF]$", message = "성별은 'M' 또는 'F' 중 하나여야 합니다.") // ^...$ 추가로 더 명확한 패턴
    private String gender; 

    @NotBlank(message = "생년월일은 필수 입력 항목입니다.")
    private String birthDate;
}