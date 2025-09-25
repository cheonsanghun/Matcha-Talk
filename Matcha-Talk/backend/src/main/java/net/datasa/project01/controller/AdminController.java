// backend/src/main/java/net/datasa/project01/controller/AdminController.java
package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.InquiryResponse;
import net.datasa.project01.domain.dto.ReportResponse;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserPenalty;
import net.datasa.project01.service.AdminService;          // 사용자 관리(계정/잠금/권한/제재 조회)는 계속 AdminService 사용
import net.datasa.project01.service.AdminSupportService;  // 문의/신고 전용 서비스
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;                // 사용자 관리
    private final AdminSupportService adminSupportService;  // 문의/신고

    // ============================================================
    // 사용자 관리
    // ============================================================

    @GetMapping("/users")
    public List<User> searchUsers(@RequestParam(value = "q", required = false) String q) {
        return adminService.searchUsers(q);
    }

    @PatchMapping("/users/{id}")
    public User updateBasic(@PathVariable("id") Long userPid, @RequestBody UpdateReq req) {
        return adminService.updateUser(userPid, req.getNickName(), req.getEmail(), req.getRoleName());
    }

    @PostMapping("/users/{id}/lock")
    public User lockUser(@PathVariable("id") Long userPid, @RequestBody LockReq req) {
        long minutes = (req.getMinutes() <= 0 ? 10 : req.getMinutes());
        return adminService.lockUser(userPid, minutes);
    }

    @PatchMapping("/users/{id}/enable")
    public User enableUser(@PathVariable("id") Long userPid, @RequestBody EnableReq req) {
        return adminService.setEnabled(userPid, req.isEnabled());
    }

    @GetMapping("/users/{id}/penalties")
    public List<UserPenalty> penaltiesOfUserPath(@PathVariable("id") Long userPid) {
        return adminService.penaltiesOfUser(userPid);
    }

    @GetMapping("/penalties")
    public List<UserPenalty> penaltiesOfUserQuery(@RequestParam("userPid") Long userPid) {
        return adminService.penaltiesOfUser(userPid);
    }

    // ============================================================
    // 문의(1:1)
    // ============================================================

    /** 상태별 최신 200건: status 없으면 OPEN 기본 */
    @GetMapping("/inquiries")
    public List<InquiryResponse> listInquiries(@RequestParam(value = "status", required = false) String status) {
        return adminSupportService.listInquiriesByStatus(status);
    }

    /** 답변 처리 → 204(No Content) */
    @PostMapping("/inquiries/{id}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void answerInquiry(@PathVariable("id") Long id, @RequestBody AnswerReq req) {
        adminSupportService.answerInquiry(id, req.getAnswer());
    }

    /** 종결 처리 → 204(No Content) */
    @PostMapping("/inquiries/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeInquiry(@PathVariable("id") Long id) {
        adminSupportService.closeInquiry(id);
    }

    // ============================================================
    // 신고
    // ============================================================

    /** 상태별 최신 200건: status 없으면 OPEN 기본 */
    @GetMapping("/reports")
    public List<ReportResponse> listReports(@RequestParam(value = "status", required = false) String status) {
        return adminSupportService.listReportsByStatus(status);
    }

    /** 단건 상세 */
    @GetMapping("/reports/{id}")
    public ReportResponse getReport(@PathVariable("id") Long id) {
        return adminSupportService.getReport(id);
    }

    /** 신고 처리(WARN/SUSPEND/BAN) → 204(No Content)
     *  body: { "penaltyType":"SUSPEND", "days":7, "reason":"욕설" }
     */
    @PostMapping("/reports/{id}/action")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void action(@PathVariable("id") Long id, @RequestBody ActionReq req) {
        adminSupportService.actionReport(id, req.getPenaltyType(), req.getDays(), req.getReason());
    }

    /** 신고 기각 → 204(No Content) */
    @PostMapping("/reports/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable("id") Long id, @RequestBody DismissReq req) {
        adminSupportService.dismissReport(id, req.getReason());
    }

    // ============================================================
    // 요청 DTO (간단 바디용)
    // ============================================================

    public static class LockReq {
        private long minutes;
        public long getMinutes() { return minutes; }
        public void setMinutes(long m) { this.minutes = m; }
    }

    public static class EnableReq {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
    }

    public static class UpdateReq {
        private String nickName;
        private String email;
        private String roleName;
        public String getNickName() { return nickName; }
        public void setNickName(String v) { this.nickName = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String v) { this.roleName = v; }
    }

    public static class AnswerReq {
        private String answer;
        public String getAnswer() { return answer; }
        public void setAnswer(String v) { this.answer = v; }
    }

    public static class ActionReq {
        private String penaltyType; // WARN / SUSPEND / BAN
        private Integer days;       // SUSPEND일 때만 사용
        private String reason;
        public String getPenaltyType() { return penaltyType; }
        public void setPenaltyType(String v) { this.penaltyType = v; }
        public Integer getDays() { return days; }
        public void setDays(Integer v) { this.days = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }

    public static class DismissReq {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }
}
