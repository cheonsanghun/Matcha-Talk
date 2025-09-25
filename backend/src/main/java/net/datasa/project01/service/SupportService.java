// backend/src/main/java/net/datasa/project01/service/SupportService.java
package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserInquiry;
import net.datasa.project01.domain.entity.UserReport;
import net.datasa.project01.repository.jpa.SpringDataUserJpaRepository;
import net.datasa.project01.repository.jpa.UserInquiryRepository;
import net.datasa.project01.repository.jpa.UserReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SpringDataUserJpaRepository userRepo;
    private final UserInquiryRepository inquiryRepo;
    private final UserReportRepository reportRepo;

    // =========================
    // 1) 문의
    // =========================

    /** 문의 생성 → DTO 반환 */
    @Transactional
    public InquiryResponse createInquiry(Long userPid, String category, String title, String content) {
        User u = userRepo.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        // DDL상 category NOT NULL이므로 널/공백 방지 + 대문자 정규화
        String normCategory = (category == null ? "" : category).trim().toUpperCase();
        if (normCategory.isEmpty()) throw new IllegalArgumentException("category is required");

        UserInquiry saved = inquiryRepo.save(
                UserInquiry.builder()
                        .user(u)
                        .category(normCategory)
                        .title(title)
                        .content(content)
                        .status("OPEN")
                        .build()
        );

        return InquiryResponse.of(saved);
    }

    /** 내 문의 목록(최신 200) → DTO 리스트 */
    @Transactional(readOnly = true)
    public List<InquiryResponse> myInquiries(Long userPid) {
        return inquiryRepo.findTop200ByUser_UserPidOrderByInquiryIdDesc(userPid)
                .stream()
                .map(InquiryResponse::of)
                .toList();
    }

    // =========================
    // 2) 신고
    // =========================

    /** 신고 생성(PID로 대상 지정) → DTO 반환 */
    @Transactional
    public ReportResponse createReport(Long reporterPid, Long reportedPid, String reason, String detail) {
        if (reporterPid == null || reportedPid == null)
            throw new IllegalArgumentException("reporter/reported is required");
        if (reporterPid.equals(reportedPid))
            throw new IllegalArgumentException("cannot report yourself");

        User reporter = userRepo.findById(reporterPid)
                .orElseThrow(() -> new IllegalArgumentException("reporter not found"));
        User reported = userRepo.findById(reportedPid)
                .orElseThrow(() -> new IllegalArgumentException("reported user not found"));

        String normReason = reason == null ? "" : reason.trim();
        if (normReason.isEmpty()) throw new IllegalArgumentException("reason is required");

        UserReport saved = reportRepo.save(
                UserReport.builder()
                        .reporter(reporter)
                        .reported(reported)
                        .reason(normReason)
                        .detail((detail == null) ? null : detail.trim())
                        .status("OPEN")
                        .build()
        );
        return ReportResponse.of(saved);
    }

    /** 신고 생성(로그인 아이디로 대상 지정) → DTO 반환 */
    @Transactional
    public ReportResponse createReportByLogin(Long reporterPid, String reportedLoginId, String reason, String detail) {
        if (reporterPid == null)
            throw new IllegalArgumentException("reporterPid is required");
        String login = (reportedLoginId == null ? "" : reportedLoginId.trim());
        if (login.isEmpty())
            throw new IllegalArgumentException("reportedLoginId is required");

        User reporter = userRepo.findById(reporterPid)
                .orElseThrow(() -> new IllegalArgumentException("reporter not found"));
        User reported = userRepo.findByLoginId(login)
                .orElseThrow(() -> new IllegalArgumentException("reported user not found"));

        if (reporter.getUserPid().equals(reported.getUserPid()))
            throw new IllegalArgumentException("cannot report yourself");

        String normReason = reason == null ? "" : reason.trim();
        if (normReason.isEmpty()) throw new IllegalArgumentException("reason is required");

        UserReport saved = reportRepo.save(
                UserReport.builder()
                        .reporter(reporter)
                        .reported(reported)
                        .reason(normReason)
                        .detail((detail == null || detail.isBlank()) ? null : detail.trim())
                        .status("OPEN")
                        .build()
        );
        return ReportResponse.of(saved);
    }

    /** 내가 올린 신고 목록(최신 200) → DTO 리스트 */
    @Transactional(readOnly = true)
    public List<ReportResponse> myReports(Long reporterPid) {
        return reportRepo.findTop200ByReporter_UserPidOrderByReportIdDesc(reporterPid)
                .stream()
                .map(ReportResponse::of)
                .toList();
    }
}
