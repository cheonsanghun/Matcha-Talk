package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.datasa.project01.domain.entity.User;

@Getter
@AllArgsConstructor
public class FollowResponseDto {

    private final String loginId;
    private final String nickName;

    public static FollowResponseDto fromUser(User user) {
        return new FollowResponseDto(user.getLoginId(), user.getNickName());
    }
}
