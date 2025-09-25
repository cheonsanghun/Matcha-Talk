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
public class AdminService {

    private final SpringDataUserJpaRepository userRepo;
    private final UserInquiryRepository      inquiryRepo;
    private final UserReportRepository       reportRepo;
    private final UserPenaltyRepository      penaltyRepo;

    // ============================================================
    //                          사용자
    // ============================================================

    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword) {
        final String k = (keyword == null) ? "" : keyword.trim();
        return userRepo.findTop100ByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(k, k);
        // users.created_at 이 없다면 ↓ 주석 해제
        // return userRepo.findTop100ByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByUserPidDesc(k, k);
    }

    public User lockUser(Long userPid, long minutes) {
        final User u = userRepo.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        final long min = Math.max(1, minutes);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(min));
        u.setFailedLoginCount(0);
        return userRepo.save(u);
    }

    public User setEnabled(Long userPid, boolean enabled) {
        final User u = userRepo.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        u.setEnabled(enabled);
        if (enabled) {
            u.setLockedUntil(null);
            u.setFailedLoginCount(0);
        }
        return userRepo.save(u);
    }

    public User updateUser(Long userPid, String nick, String email, String roleName) {
        final User u = userRepo.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (nick != null && !nick.isBlank())   u.setNickName(nick.trim());
        if (email != null && !email.isBlank()) u.setEmail(email.trim());
        if (roleName != null && !roleName.isBlank()) u.setRoleName(roleName.trim());

        return userRepo.save(u);
    }

    // ============================================================
    //                            문의 (★ DTO로 반환)
    // ============================================================

    @Transactional(readOnly = true)
    public List<InquiryResponse> listInquiries(String status) {
        final String st = (status == null || status.isBlank()) ? "OPEN" : status.trim().toUpperCase();
        return inquiryRepo.findTop200ByStatusOrderByCreatedAtDesc(st)
                .stream().map(InquiryResponse::of).toList();
    }

    /** 답변 완료 처리 + DTO로 즉시 변환(세션 안에서 변환) */
    public InquiryResponse answerInquiry(Long inquiryId, String answer) {
        final UserInquiry q = inquiryRepo.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        q.setStatus("ANSWERED");
        q.setAnsweredAt(LocalDateTime.now());
        final UserInquiry saved = inquiryRepo.save(q);

        // LAZY 초기화(필요시)
        if (saved.getUser() != null) saved.getUser().getUserPid();

        return InquiryResponse.of(saved);
    }

    /** 문의 종결 + DTO */
    public InquiryResponse closeInquiry(Long inquiryId) {
        final UserInquiry q = inquiryRepo.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("inquiry not found"));
        q.setStatus("CLOSED");
        final UserInquiry saved = inquiryRepo.save(q);

        if (saved.getUser() != null) saved.getUser().getUserPid();

        return InquiryResponse.of(saved);
    }

    // ============================================================
    //                         신고 / 제재 (★ DTO로 반환)
    // ============================================================

    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(String status) {
        final String st = (status == null || status.isBlank()) ? "OPEN" : status.trim().toUpperCase();
        return reportRepo.findTop200ByStatusOrderByCreatedAtDesc(st)
                .stream().map(ReportResponse::of).toList();
    }

    public ReportResponse actionReport(Long reportId, String penaltyType, Integer days, String reason) {
        final UserReport r = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        final User target = r.getReported();

        final String type = (penaltyType == null) ? "" : penaltyType.trim().toUpperCase();
        if (!(type.equals("WARN") || type.equals("SUSPEND") || type.equals("BAN"))) {
            throw new IllegalArgumentException("invalid penalty type");
        }

        final LocalDateTime now  = LocalDateTime.now();
        LocalDateTime ends = null;
        if (type.equals("SUSPEND")) {
            final int d = (days == null || days <= 0) ? 7 : days;
            ends = now.plusDays(d);
        }

        // 제재 기록
        final UserPenalty p = UserPenalty.builder()
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
        final UserReport saved = reportRepo.save(r);

        if (saved.getReporter()!=null) saved.getReporter().getUserPid();
        if (saved.getReported()!=null) saved.getReported().getUserPid();

        return ReportResponse.of(saved);
    }

    public ReportResponse dismissReport(Long reportId, String reason) {
        final UserReport r = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        r.setStatus("DISMISSED");
        if (reason != null && !reason.isBlank()) {
            final String d = (r.getDetail() == null ? "" : (r.getDetail() + "\n"))
                    + "[DISMISS_REASON] " + reason.trim();
            r.setDetail(d);
        }
        final UserReport saved = reportRepo.save(r);

        if (saved.getReporter()!=null) saved.getReporter().getUserPid();
        if (saved.getReported()!=null) saved.getReported().getUserPid();

        return ReportResponse.of(saved);
    }

    // ============================================================
    // 제재 이력
    // ============================================================
    @Transactional(readOnly = true)
    public List<UserPenalty> penaltiesOfUser(Long userPid) {
        return penaltyRepo.findByUser_UserPidOrderByStartsAtDesc(userPid);
    }
}
