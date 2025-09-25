package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserPenalty;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserPenaltyCreateDto {

    private Long userPid;
    private String type; // "WARN", "SUSPEND", "BAN"
    private String reason;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    public UserPenalty toEntity(User user) {
        return UserPenalty.builder()
                .user(user)
                .type(UserPenalty.PenaltyType.valueOf(this.type))
                .reason(this.reason)
                .startsAt(this.startsAt)
                .endsAt(this.endsAt)
                .build();
    }
}
