package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryCreateRequest;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportCreateByLoginRequest;
import net.datasa.project01.domain.dto.ReportCreateRequest;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.service.SupportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/inquiries")
    public ResponseEntity<InquiryResponse> createInquiry(@AuthenticationPrincipal(expression = "username") String loginId,
                                                         @Valid @RequestBody InquiryCreateRequest req) {
        InquiryResponse response = supportService.createInquiry(
                loginId, req.getCategory(), req.getTitle(), req.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inquiries")
    public ResponseEntity<List<InquiryResponse>> myInquiries(@AuthenticationPrincipal(expression = "username") String loginId) {
        return ResponseEntity.ok(supportService.myInquiries(loginId));
    }

    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> createReport(@AuthenticationPrincipal(expression = "username") String loginId,
                                                       @Valid @RequestBody ReportCreateRequest req) {
        ReportResponse response = supportService.createReport(
                loginId, req.getReportedPid(), req.getReason(), req.getDetail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reports/by-login")
    public ResponseEntity<ReportResponse> createReportByLogin(@AuthenticationPrincipal(expression = "username") String loginId,
                                                              @Valid @RequestBody ReportCreateByLoginRequest req) {
        ReportResponse response = supportService.createReportByLogin(
                loginId, req.getReportedLoginId(), req.getReason(), req.getDetail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportResponse>> myReports(@AuthenticationPrincipal(expression = "username") String loginId) {
        return ResponseEntity.ok(supportService.myReports(loginId));
    }
}
