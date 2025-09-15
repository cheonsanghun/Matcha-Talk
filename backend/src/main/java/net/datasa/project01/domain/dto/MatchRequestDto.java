package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 매칭 요청 DTO. 유효성 검사 규칙을 포함
 */
@Getter
@Setter
public class MatchRequestDto {

    @NotBlank(message = "희망 성별은 필수 선택 항목입니다.")
    @Pattern(regexp = "^[MFA]$", message = "희망 성별은 'M', 'F', 'A' 중 하나여야 합니다.")
    private String choiceGender;

    @NotNull(message = "최소 나이는 필수 입력 항목입니다.")
    @Min(value = 18, message = "최소 나이는 18세 이상이어야 합니다.")
    private Integer minAge;

    @NotNull(message = "최대 나이는 필수 입력 항목입니다.")
    @Max(value = 99, message = "최대 나이는 99세 이하이어야 합니다.")
    private Integer maxAge;

    @NotBlank(message = "희망 지역 코드는 필수 입력 항목입니다.")
    private String regionCode;

    @NotEmpty(message = "관심사는 최소 1개 이상 선택해야 합니다.")
    private List<String> interests; // 'interestsJson' -> 'interests'로 필드명 변경
}