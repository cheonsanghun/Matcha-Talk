package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.UserReport;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
@AllArgsConstructor
public class UserReportResponseDto {

    private final Long reportId;
    private final UserSummary reporter;
    private final UserSummary reported;
    private final String reason;
    private final String detail;
    private final String status;
    private final String createdAt;

    public static UserReportResponseDto fromEntity(UserReport report) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        UserSummary reporterSummary = UserSummary.fromEntity(report.getReporter());
        UserSummary reportedSummary = UserSummary.fromEntity(report.getReported());

        return UserReportResponseDto.builder()
                .reportId(report.getReportId())
                .reporter(reporterSummary)
                .reported(reportedSummary)
                .reason(report.getReason())
                .detail(report.getDetail())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt().format(formatter))
                .build();
    }
}
