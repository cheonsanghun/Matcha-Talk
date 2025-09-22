package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.FollowList;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class FollowListItemDto {
    private final Long followId;
    private final Long userPid;
    private final String loginId;
    private final String nickName;
    private final Follow.Status status;
    private final FollowList.Direction direction;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
