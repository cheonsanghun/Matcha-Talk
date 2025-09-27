package net.datasa.project01.domain.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;
import net.datasa.project01.domain.entity.User;

@Value
@Builder
public class AdminUserResponse {
    Long userPid;
    String loginId;
    String email;
    String nickName;
    String roleName;
    boolean enabled;
    LocalDateTime lockedUntil;
    Integer failedLoginCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static AdminUserResponse of(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        return AdminUserResponse.builder()
                .userPid(user.getUserPid())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .nickName(user.getNickName())
                .roleName(user.getRoleName())
                .enabled(user.isEnabled())
                .lockedUntil(user.getLockedUntil())
                .failedLoginCount(user.getFailedLoginCount())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
