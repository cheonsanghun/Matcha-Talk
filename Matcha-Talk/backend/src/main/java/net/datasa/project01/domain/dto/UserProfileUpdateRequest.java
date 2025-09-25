package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class UserProfileUpdateRequest {

    @Size(min = 1, max = 30, message = "닉네임은 1~30자여야 합니다.")
    private String nickName;

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$", message = "Gmail 주소만 사용 가능합니다.")
    private String email;

    @Pattern(regexp = "^[A-Z]{2}$", message = "국가코드는 대문자 2자리여야 합니다.")
    private String countryCode;

    @Pattern(regexp = "^[MFU]$", message = "성별은 M/F/U 중 하나여야 합니다.")
    private String gender;

    private LocalDate birthDate;

    @Size(max = 300)
    private String avatarUrl;

    @Size(max = 500)
    private String bio;

    private List<@Size(max = 20) String> languages;

    @Pattern(regexp = "^(PUBLIC|FRIENDS|PRIVATE)$", message = "공개 범위는 PUBLIC/FRIENDS/PRIVATE 중 하나여야 합니다.")
    private String visibility;
}
