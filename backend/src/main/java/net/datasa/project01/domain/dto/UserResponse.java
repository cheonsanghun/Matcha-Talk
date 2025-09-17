// UserResponse.java
package net.datasa.project01.domain.dto;

import lombok.*;
import net.datasa.project01.domain.entity.User;

import java.time.LocalDate;

/**
 * 회원 정보 응답 DTO
 * - 회원 조회, 상세 정보 반환 등에 사용
 * - 민감 정보(비밀번호 등)는 포함하지 않음
 */
@Getter
@Builder
public class UserResponse {
    private final Long userPid;
    private final String loginId;
    private final String nickName;
    private final String email;
    private final String countryCode;
    private final Character gender;
    private final LocalDate birthDate;
    private final String roleName;
    private final boolean enabled;

    /**
     * User 엔티티를 UserResponse DTO로 변환하는 정적 팩토리 메소드
     * @param user 변환할 User 엔티티
     * @return 변환된 UserResponse DTO
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .userPid(user.getUserPid())
                .loginId(user.getLoginId())
                .nickName(user.getNickName())
                .email(user.getEmail())
                .countryCode(user.getCountryCode())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .roleName(user.getRoleName())
                .enabled(user.isEnabled())
                .build();
    }
}