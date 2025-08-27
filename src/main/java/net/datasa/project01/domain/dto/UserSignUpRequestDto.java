package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 회원가입 시 클라이언트가 보내는 데이터 형식
 *  - 비밀번호는 평문으로 받고, 서비스에서 BCrypt로 해시하여 저장
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserSignUpRequestDto {

    @NotBlank
    @Size(min = 4, max = 30, message = "loginId는 4~30자")
    private String loginId;

    @NotBlank
    @Size(min = 8, max = 64, message = "password는 8~64자")
    private String password; // 평문 (서버에서 해시 처리)

    @NotBlank
    @Size(min = 1, max = 30)
    private String nickName;

    @Email
    private String email; // 선택

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode는 대문자 2자리(KR/JP 등)")
    private String countryCode;

    @NotBlank
    @Pattern(regexp = "^[MF]$", message = "gender는 M 또는 F")
    private String gender;

    @NotNull
    private LocalDate birthDate;
}