package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.User;

@Getter
@Builder
@AllArgsConstructor
public class FollowResponseDto {

    private final Long followId;
    private final Long userId;
    private final String nickname;
    private final String avatarUrl;
    private final String status;

    /**
     * Follow 엔티티를 DTO로 변환하는 정적 메서드
     * @param follow 팔로우 엔티티
     * @param targetUser 표시할 대상 사용자 (follower 또는 followee)
     * @return FollowResponseDto
     */
    public static FollowResponseDto fromEntity(Follow follow, User targetUser) {
        String avatar = (targetUser.getProfile() != null) ? targetUser.getProfile().getAvatarUrl() : null;

        return FollowResponseDto.builder()
                .followId(follow.getFollowId())
                .userId(targetUser.getUserPid())
                .nickname(targetUser.getNickName())
                .avatarUrl(avatar)
                .status(follow.getStatus().name())
                .build();
    }
}
