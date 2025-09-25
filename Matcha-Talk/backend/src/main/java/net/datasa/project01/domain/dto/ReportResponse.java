// backend/src/main/java/net/datasa/project01/domain/dto/ReportResponse.java
package net.datasa.project01.domain.dto;

import lombok.*;
import net.datasa.project01.domain.entity.UserReport;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ReportResponse {
    private Long reportId;

    private Long reporterPid;
    private String reporterLoginId;

    private Long reportedPid;
    private String reportedLoginId;

    private String reason;
    private String detail;
    private String status;
    private LocalDateTime createdAt;

    public static ReportResponse of(UserReport r){
        return ReportResponse.builder()
                .reportId(r.getReportId())
                .reporterPid(r.getReporter().getUserPid())
                .reporterLoginId(r.getReporter().getLoginId())
                .reportedPid(r.getReported().getUserPid())
                .reportedLoginId(r.getReported().getLoginId())
                .reason(r.getReason())
                .detail(r.getDetail())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
