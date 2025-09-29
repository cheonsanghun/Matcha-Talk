package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.User;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private Long userPid;
    private String loginId;
    private String nickname;
    private String email;

    public static UserSummary fromEntity(User user) {
        return UserSummary.builder()
                .id(user.getUserPid())
                .userPid(user.getUserPid())
                .loginId(user.getLoginId())
                .nickname(user.getNickName())
                .email(user.getEmail())
                .build();
    }
}