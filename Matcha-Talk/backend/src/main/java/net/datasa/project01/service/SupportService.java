package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserInquiry;
import net.datasa.project01.domain.entity.UserReport;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.repository.jpa.UserInquiryRepository;
import net.datasa.project01.repository.jpa.UserReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SupportService {

    private final UserRepository userRepository;
    private final UserInquiryRepository inquiryRepository;
    private final UserReportRepository reportRepository;

    /* ==============================
     * 문의 등록/조회
     * ============================== */
    public InquiryResponse createInquiry(String loginId, String category, String title, String content) {
        User user = requireUser(loginId);

        String normCategory = category == null ? "" : category.trim().toUpperCase();
        if (normCategory.isEmpty()) {
            throw new IllegalArgumentException("category is required");
        }

        UserInquiry inquiry = UserInquiry.builder()
                .user(user)
                .category(normCategory)
                .title(title)
                .content(content)
                .status(UserInquiry.InquiryStatus.OPEN)
                .build();

        UserInquiry saved = inquiryRepository.save(inquiry);
        return InquiryResponse.of(saved);
    }

    @Transactional(readOnly = true)
    public List<InquiryResponse> myInquiries(String loginId) {
        User user = requireUser(loginId);
        return inquiryRepository.findTop200ByUser_UserPidOrderByInquiryIdDesc(user.getUserPid())
                .stream()
                .map(InquiryResponse::of)
                .toList();
    }

    /* ==============================
     * 신고 등록/조회
     * ============================== */
    public ReportResponse createReport(String reporterLoginId, Long reportedPid, String reason, String detail) {
        if (reportedPid == null) {
            throw new IllegalArgumentException("reportedPid is required");
        }
        User reporter = requireUser(reporterLoginId);
        if (reporter.getUserPid().equals(reportedPid)) {
            throw new IllegalArgumentException("cannot report yourself");
        }

        User reported = userRepository.findById(reportedPid)
                .orElseThrow(() -> new IllegalArgumentException("reported user not found"));

        String normReason = reason == null ? "" : reason.trim();
        if (normReason.isEmpty()) {
            throw new IllegalArgumentException("reason is required");
        }

        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reported(reported)
                .reason(normReason)
                .detail(detail == null ? null : detail.trim())
                .status(UserReport.ReportStatus.OPEN)
                .build();

        UserReport saved = reportRepository.save(report);
        return ReportResponse.of(saved);
    }

    public ReportResponse createReportByLogin(String reporterLoginId, String reportedLoginId, String reason, String detail) {
        String loginId = reportedLoginId == null ? "" : reportedLoginId.trim();
        if (loginId.isEmpty()) {
            throw new IllegalArgumentException("reportedLoginId is required");
        }

        User reporter = requireUser(reporterLoginId);
        User reported = userRepository.findByLoginId(loginId.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("reported user not found"));

        if (reporter.getUserPid().equals(reported.getUserPid())) {
            throw new IllegalArgumentException("cannot report yourself");
        }

        String normReason = reason == null ? "" : reason.trim();
        if (normReason.isEmpty()) {
            throw new IllegalArgumentException("reason is required");
        }

        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reported(reported)
                .reason(normReason)
                .detail((detail == null || detail.isBlank()) ? null : detail.trim())
                .status(UserReport.ReportStatus.OPEN)
                .build();

        UserReport saved = reportRepository.save(report);
        return ReportResponse.of(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> myReports(String reporterLoginId) {
        User reporter = requireUser(reporterLoginId);
        return reportRepository.findTop200ByReporter_UserPidOrderByReportIdDesc(reporter.getUserPid())
                .stream()
                .map(ReportResponse::of)
                .toList();
    }

    private User requireUser(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalStateException("login is required");
        }
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }
}
