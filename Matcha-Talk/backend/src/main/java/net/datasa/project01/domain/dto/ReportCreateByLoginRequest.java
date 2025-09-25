package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReportCreateByLoginRequest {
    @NotNull
    private Long reporterPid;        // 신고자 PID (로그인 사용자)

    @NotBlank
    private String reportedLoginId;  // ✅ 피신고자 로그인 아이디

    @NotBlank
    private String reason;

    private String detail;           // 선택
}
