package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.UserPenalty;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
@AllArgsConstructor
public class UserPenaltyResponseDto {

    private final Long penaltyId;
    private final UserSummary user;
    private final String type;
    private final String reason;
    private final String startsAt;
    private final String endsAt;
    private final String createdAt;

    public static UserPenaltyResponseDto fromEntity(UserPenalty penalty) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String formattedEndsAt = (penalty.getEndsAt() != null) ? penalty.getEndsAt().format(formatter) : null;

        UserSummary userSummary = UserSummary.builder()
                .id(penalty.getUser().getUserPid())
                .loginId(penalty.getUser().getLoginId())
                .nickname(penalty.getUser().getNickName())
                .email(penalty.getUser().getEmail())
                .build();

        return UserPenaltyResponseDto.builder()
                .penaltyId(penalty.getPenaltyId())
                .user(userSummary)
                .type(penalty.getType().name())
                .reason(penalty.getReason())
                .startsAt(penalty.getStartsAt().format(formatter))
                .endsAt(formattedEndsAt)
                .createdAt(penalty.getCreatedAt().format(formatter))
                .build();
    }
}
