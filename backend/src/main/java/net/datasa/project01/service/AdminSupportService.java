// backend/src/main/java/net/datasa/project01/service/AdminSupportService.java
package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserInquiry;
import net.datasa.project01.domain.entity.UserPenalty;
import net.datasa.project01.domain.entity.UserReport;
import net.datasa.project01.repository.jpa.SpringDataUserJpaRepository;
import net.datasa.project01.repository.jpa.UserInquiryRepository;
import net.datasa.project01.repository.jpa.UserPenaltyRepository;
import net.datasa.project01.repository.jpa.UserReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminSupportService {

    private final SpringDataUserJpaRepository userRepo;
    private final UserInquiryRepository inquiryRepo;
    private final UserReportRepository reportRepo;
    private final UserPenaltyRepository penaltyRepo;

    // ==============================
    // 1) 문의
    // ==============================

    /** 상태별 문의 목록 조회 */
    @Transactional(readOnly = true)
    public List<InquiryResponse> listInquiriesByStatus(String status) {
        final String st = (status == null || status.isBlank()) ? "OPEN" : status.trim().toUpperCase();
        return inquiryRepo.findTop200ByStatusOrderByCreatedAtDesc(st)
                .stream().map(InquiryResponse::of).toList();
    }

    /** 문의 답변 처리 */
    public InquiryResponse answerInquiry(Long inquiryId, String answer) {
        UserInquiry q = inquiryRepo.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        q.setStatus("ANSWERED");
        q.setAnsweredAt(LocalDateTime.now());
        UserInquiry saved = inquiryRepo.save(q);
        if (saved.getUser() != null) saved.getUser().getUserPid(); // LAZY 초기화
        return InquiryResponse.of(saved);
    }

    /** 문의 종결 처리 */
    public InquiryResponse closeInquiry(Long inquiryId) {
        UserInquiry q = inquiryRepo.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        q.setStatus("CLOSED");
        UserInquiry saved = inquiryRepo.save(q);
        if (saved.getUser() != null) saved.getUser().getUserPid();
        return InquiryResponse.of(saved);
    }

    // ==============================
    // 2) 신고
    // ==============================

    /** 상태별 신고 목록 조회 */
    @Transactional(readOnly = true)
    public List<ReportResponse> listReportsByStatus(String status) {
        final String st = (status == null || status.isBlank()) ? "OPEN" : status.trim().toUpperCase();
        return reportRepo.findTop200ByStatusOrderByCreatedAtDesc(st)
                .stream().map(ReportResponse::of).toList();
    }

    /** 단건 신고 상세 조회 */
    @Transactional(readOnly = true)
    public ReportResponse getReport(Long reportId) {
        UserReport r = reportRepo.findByReportId(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        return ReportResponse.of(r);
    }

    /** 신고 처리 (WARN / SUSPEND / BAN) */
    public ReportResponse actionReport(Long reportId, String penaltyType, Integer days, String reason) {
        UserReport r = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        User target = r.getReported();

        String type = (penaltyType == null) ? "" : penaltyType.trim().toUpperCase();
        if (!(type.equals("WARN") || type.equals("SUSPEND") || type.equals("BAN"))) {
            throw new IllegalArgumentException("invalid penalty type");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ends = null;
        if (type.equals("SUSPEND")) {
            int d = (days == null || days <= 0) ? 7 : days;
            ends = now.plusDays(d);
        }

        // 제재 기록 생성
        UserPenalty p = UserPenalty.builder()
                .user(target)
                .type(type)
                .reason(reason)
                .startsAt(now)
                .endsAt(type.equals("BAN") ? null : ends)
                .build();
        penaltyRepo.save(p);

        // 사용자 상태 반영
        switch (type) {
            case "WARN" -> {}
            case "SUSPEND" -> {
                target.setEnabled(false);
                target.setLockedUntil(ends);
                userRepo.save(target);
            }
            case "BAN" -> {
                target.setEnabled(false);
                target.setLockedUntil(null);
                userRepo.save(target);
            }
        }

        r.setStatus("ACTIONED");
        UserReport saved = reportRepo.save(r);
        if (saved.getReporter() != null) saved.getReporter().getUserPid();
        if (saved.getReported() != null) saved.getReported().getUserPid();
        return ReportResponse.of(saved);
    }

    /** 신고 기각 */
    public ReportResponse dismissReport(Long reportId, String reason) {
        UserReport r = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        r.setStatus("DISMISSED");
        if (reason != null && !reason.isBlank()) {
            String d = (r.getDetail() == null ? "" : (r.getDetail() + "\n"))
                    + "[DISMISS_REASON] " + reason.trim();
            r.setDetail(d);
        }
        UserReport saved = reportRepo.save(r);
        if (saved.getReporter() != null) saved.getReporter().getUserPid();
        if (saved.getReported() != null) saved.getReported().getUserPid();
        return ReportResponse.of(saved);
    }
}
