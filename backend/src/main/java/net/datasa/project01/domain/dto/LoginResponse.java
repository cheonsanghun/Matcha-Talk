package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 성공 시 반환되는 JWT 액세스 토큰과 사용자 요약 정보.
 */
@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {

    private final String token;
    private final UserSummary user;

    public static LoginResponse of(String token, UserSummary user) {
        return LoginResponse.builder()
                .token(token)
                .user(user)
                .build();
    }
}
