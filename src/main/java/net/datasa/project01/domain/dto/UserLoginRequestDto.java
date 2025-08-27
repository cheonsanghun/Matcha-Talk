package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 로그인 시 필요한 최소 데이터
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserLoginRequestDto {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;
}