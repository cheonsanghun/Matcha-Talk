package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Follow;

@Getter
@Builder
@AllArgsConstructor
public class FollowResponseDto {
    private final Long followId;
    private final Long followerPid;
    private final String followerLoginId;
    private final String followerNickName;
    private final Long followeePid;
    private final String followeeLoginId;
    private final String followeeNickName;
    private final Follow.Status status;
}
