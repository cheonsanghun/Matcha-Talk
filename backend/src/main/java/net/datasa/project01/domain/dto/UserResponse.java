package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.User;

import java.time.LocalDate;

@Getter
@Builder // 빌더 패턴으로 객체를 생성
public class UserResponse {
    private final Long userPid;
    private final String loginId;
    private final String nickName;
    private final String email;
    private final String countryCode;
    private final Character gender;
    private final LocalDate birthDate;
    private final String rolename;
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
                .rolename(user.getRolename())
                .enabled(user.isEnabled())
                .build();
    }
}