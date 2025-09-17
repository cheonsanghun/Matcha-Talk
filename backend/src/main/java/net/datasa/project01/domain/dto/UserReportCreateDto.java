package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserReport;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserReportCreateDto {

    private Long reportedPid;
    private String reason;
    private String detail;

    public UserReport toEntity(User reporter, User reported) {
        return UserReport.builder()
                .reporter(reporter)
                .reported(reported)
                .reason(this.reason)
                .detail(this.detail)
                .build(); // Status defaults to OPEN in the entity
    }
}
