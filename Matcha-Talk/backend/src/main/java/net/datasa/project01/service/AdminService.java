package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserInquiry;
import net.datasa.project01.domain.entity.UserPenalty;
import net.datasa.project01.domain.entity.UserReport;
import net.datasa.project01.repository.UserRepository;
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
public class AdminService {

    private final UserRepository userRepository;
    private final UserInquiryRepository inquiryRepository;
    private final UserReportRepository reportRepository;
    private final UserPenaltyRepository penaltyRepository;

    /* ==============================
     * 사용자 관리
     * ============================== */
    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword) {
        final String term = keyword == null ? "" : keyword.trim();
        return userRepository
                .findTop100ByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(term, term);
    }

    public User lockUser(Long userPid, long minutes) {
        User user = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        long min = Math.max(1, minutes);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(min));
        user.setFailedLoginCount(0);
        return userRepository.save(user);
    }

    public User setEnabled(Long userPid, boolean enabled) {
        User user = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        user.setEnabled(enabled);
        if (enabled) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        return userRepository.save(user);
    }

    public User updateUser(Long userPid, String nickName, String email, String roleName) {
        User user = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (nickName != null && !nickName.isBlank()) {
            user.setNickName(nickName.trim());
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(email.trim().toLowerCase());
        }
        if (roleName != null && !roleName.isBlank()) {
            user.setRoleName(roleName.trim());
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserPenalty> penaltiesOfUser(Long userPid) {
        return penaltyRepository.findByUser_UserPidOrderByStartsAtDesc(userPid);
    }

    /* ==============================
     * 문의 관리
     * ============================== */
    @Transactional(readOnly = true)
    public List<InquiryResponse> listInquiries(String status) {
        UserInquiry.InquiryStatus inquiryStatus = status == null || status.isBlank()
                ? UserInquiry.InquiryStatus.OPEN
                : UserInquiry.InquiryStatus.valueOf(status.trim().toUpperCase());
        return inquiryRepository.findTop200ByStatusOrderByCreatedAtDesc(inquiryStatus)
                .stream()
                .map(InquiryResponse::of)
                .toList();
    }

    public InquiryResponse answerInquiry(Long inquiryId, String answer) {
        UserInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        inquiry.setStatus(UserInquiry.InquiryStatus.ANSWERED);
        inquiry.setAnsweredAt(LocalDateTime.now());
        UserInquiry saved = inquiryRepository.save(inquiry);
        if (saved.getUser() != null) saved.getUser().getUserPid();
        return InquiryResponse.of(saved);
    }

    public InquiryResponse closeInquiry(Long inquiryId) {
        UserInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        inquiry.setStatus(UserInquiry.InquiryStatus.CLOSED);
        UserInquiry saved = inquiryRepository.save(inquiry);
        if (saved.getUser() != null) saved.getUser().getUserPid();
        return InquiryResponse.of(saved);
    }

    /* ==============================
     * 신고/제재 관리
     * ============================== */
    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(String status) {
        UserReport.ReportStatus reportStatus = status == null || status.isBlank()
                ? UserReport.ReportStatus.OPEN
                : UserReport.ReportStatus.valueOf(status.trim().toUpperCase());
        return reportRepository.findTop200ByStatusOrderByCreatedAtDesc(reportStatus)
                .stream()
                .map(ReportResponse::of)
                .toList();
    }

    public ReportResponse actionReport(Long reportId, String penaltyType, Integer days, String reason) {
        UserReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        User target = report.getReported();

        if (penaltyType == null || penaltyType.isBlank()) {
            throw new IllegalArgumentException("penalty type is required");
        }
        UserPenalty.PenaltyType type;
        try {
            type = UserPenalty.PenaltyType.valueOf(penaltyType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid penalty type");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ends = null;
        if (type == UserPenalty.PenaltyType.SUSPEND) {
            int d = days == null || days <= 0 ? 7 : days;
            ends = now.plusDays(d);
        }

        UserPenalty penalty = UserPenalty.builder()
                .user(target)
                .type(type)
                .reason(reason)
                .startsAt(now)
                .endsAt(type == UserPenalty.PenaltyType.BAN ? null : ends)
                .build();
        penaltyRepository.save(penalty);

        switch (type) {
            case WARN -> { /* no-op */ }
            case SUSPEND -> {
                target.setEnabled(false);
                target.setLockedUntil(ends);
                userRepository.save(target);
            }
            case BAN -> {
                target.setEnabled(false);
                target.setLockedUntil(null);
                userRepository.save(target);
            }
        }

        report.setStatus(UserReport.ReportStatus.ACTIONED);
        UserReport saved = reportRepository.save(report);
        if (saved.getReporter() != null) saved.getReporter().getUserPid();
        if (saved.getReported() != null) saved.getReported().getUserPid();
        return ReportResponse.of(saved);
    }

    public ReportResponse dismissReport(Long reportId, String reason) {
        UserReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        report.setStatus(UserReport.ReportStatus.DISMISSED);
        if (reason != null && !reason.isBlank()) {
            String detail = (report.getDetail() == null ? "" : report.getDetail() + "\n")
                    + "[DISMISS_REASON] " + reason.trim();
            report.setDetail(detail);
        }
        UserReport saved = reportRepository.save(report);
        if (saved.getReporter() != null) saved.getReporter().getUserPid();
        if (saved.getReported() != null) saved.getReported().getUserPid();
        return ReportResponse.of(saved);
    }
}
