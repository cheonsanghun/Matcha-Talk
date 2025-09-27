// backend/src/main/java/net/datasa/project01/domain/dto/ReportCreateRequest.java
package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReportCreateRequest {
    @NotNull private Long reportedPid;          // 피신고자 (DDL과 일치)
    @NotBlank @Size(max = 100) private String reason;
    @Size(max = 4000) private String detail;
}
