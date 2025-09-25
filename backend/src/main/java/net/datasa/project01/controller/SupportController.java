// backend/src/main/java/net/datasa/project01/controller/SupportController.java
package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryCreateRequest;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportCreateRequest;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.service.SupportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    // -------------------------
    // 1) 문의
    // -------------------------

    /** 유저 문의 등록 */
    @PostMapping("/inquiries")
    public InquiryResponse createInquiry(@Valid @RequestBody InquiryCreateRequest req) {
        return supportService.createInquiry(
                req.getUserPid(), req.getCategory(), req.getTitle(), req.getContent());
    }

    /** 내가 올린 문의 목록(최신 200) */
    @GetMapping("/inquiries")
    public List<InquiryResponse> myInquiries(@RequestParam("userPid") Long userPid) {
        return supportService.myInquiries(userPid);
    }

    // -------------------------
    // 2) 신고
    // -------------------------

    /** 유저 신고 등록(PID로 대상 지정) */
    @PostMapping("/reports")
    public ReportResponse createReport(@Valid @RequestBody ReportCreateRequest req) {
        return supportService.createReport(
                req.getReporterPid(), req.getReportedPid(), req.getReason(), req.getDetail());
    }

    /** 유저 신고 등록(아이디로 대상 지정) */
    @PostMapping("/reports/by-login")
    public ReportResponse createReportByLogin(@RequestBody Map<String, Object> body) {
        Long reporterPid      = asLong(body.get("reporterPid"));
        String reportedLoginId = asStr(body.get("reportedLoginId"));
        String reason          = asStr(body.get("reason"));
        String detail          = asStrOrNull(body.get("detail"));
        return supportService.createReportByLogin(reporterPid, reportedLoginId, reason, detail);
    }

    /** 내가 올린 신고 목록(최신 200) */
    @GetMapping("/reports")
    public List<ReportResponse> myReports(@RequestParam("reporterPid") Long reporterPid) {
        return supportService.myReports(reporterPid);
    }

    // -------------------------
    // helpers
    // -------------------------
    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }
    private static String asStr(Object v) {
        return v == null ? "" : v.toString().trim();
    }
    private static String asStrOrNull(Object v) {
        String s = asStr(v);
        return s.isEmpty() ? null : s;
    }
}
