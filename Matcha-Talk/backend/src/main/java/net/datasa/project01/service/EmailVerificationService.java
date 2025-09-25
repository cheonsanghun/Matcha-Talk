// backend/src/main/java/net/datasa/project01/service/EmailVerificationService.java
package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.entity.EmailVerification;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.vo.VerificationPurpose;
import net.datasa.project01.repository.EmailVerificationRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.service.email.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository evRepository;
    private final EmailSender emailSender;
    private final Environment env;

    // 비밀번호 변경에 필요
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 숫자 토큰 길이(기본 6자리), 유효시간(초), 재요청 쿨다운(초) */
    @Value("${app.mail.token.length:6}")            private int tokenLength;
    @Value("${app.mail.token.exp-seconds:600}")     private long expSeconds;
    @Value("${app.mail.token.cooldown-seconds:60}") private long cooldownSeconds;

    private static final SecureRandom RND = new SecureRandom();

    // 강력한 비밀번호: 8~64자, 영문 대/소문자/숫자/특수문자 각 1개+
    private static final Pattern STRONG_PW =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>,.?/]).{8,64}$");

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private Pattern tokenPattern() { return Pattern.compile("^\\d{" + tokenLength + "}$"); }

    /** 0으로 시작해도 허용하는 숫자 토큰 생성기 (예: 003241) */
    private String generateNumericToken(int len) {
        int bound = (int) Math.pow(10, len); // len=6 → 1,000,000
        return String.format("%0" + len + "d", RND.nextInt(bound));
    }

    // =========================================================
    // (A) 회원가입/이메일확인용: VERIFY_EMAIL
    // =========================================================

    @Transactional
    public Map<String, Object> requestVerifyEmail(String email) {
        email = trim(email).toLowerCase();

        // (현 정책) Gmail만 허용
        if (!email.endsWith("@gmail.com")) {
            throw new IllegalArgumentException("구글 이메일만 인증 가능합니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 재요청 쿨다운
        evRepository.findLatestActiveByEmail(email, VerificationPurpose.VERIFY_EMAIL, now)
                .ifPresent(active -> {
                    LocalDateTime created = Optional.ofNullable(active.getCreatedAt()).orElse(now);
                    long elapsed = Duration.between(created, now).getSeconds();
                    if (elapsed < cooldownSeconds) {
                        long wait = cooldownSeconds - elapsed;
                        throw new IllegalArgumentException("인증번호 재요청은 " + wait + "초 후에 가능합니다.");
                    }
                });

        String token = generateNumericToken(tokenLength);
        EmailVerification ev = EmailVerification.builder()
                .email(email)
                .token(token)
                .purpose(VerificationPurpose.VERIFY_EMAIL)
                .expiresAt(now.plusSeconds(expSeconds))
                .usedAt(null)
                .build();
        evRepository.save(ev);

        String subject = "[Matcha-Talk] 이메일 인증번호";
        String html = """
            <h3>이메일 인증번호</h3>
            <p>인증번호: <b style="font-size:20px">%s</b></p>
            <p>유효시간: %d초</p>
            """.formatted(token, expSeconds);
        emailSender.send(email, subject, html);

        boolean isMock = env.matchesProfiles("mock");
        return isMock
                ? Map.of("sent", true, "dev_token", token, "expires_in_seconds", expSeconds)
                : Map.of("sent", true, "expires_in_seconds", expSeconds);
    }

    @Transactional
    public Map<String, Object> confirmVerifyEmail(String email, String token) {
        email = trim(email).toLowerCase();
        token = trim(token);

        LocalDateTime now = LocalDateTime.now();

        EmailVerification ev = evRepository
                .findByTokenAndEmail(token, email, VerificationPurpose.VERIFY_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다."));

        if (ev.getUsedAt() != null) {
            throw new IllegalArgumentException("이미 사용된 인증번호입니다.");
        }
        if (!ev.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다.");
        }

        ev.setUsedAt(now);
        evRepository.save(ev);

        return Map.of("verified", true, "email", email);
    }

    // =========================================================
    // (B) 비밀번호 재설정용: RESET_PW
    // =========================================================

    /** (B-1) 비밀번호 재설정 인증번호 요청 */
    @Transactional
    public Map<String, Object> requestResetPassword(String email) {
        email = trim(email).toLowerCase();
        LocalDateTime now = LocalDateTime.now();

        // 가입된 이메일인지 확인 (정보노출을 막고 싶으면 여기서도 항상 sent=true 응답 가능)
        userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 없습니다."));

        // 재요청 쿨다운
        evRepository.findLatestActiveByEmail(email, VerificationPurpose.RESET_PW, now)
                .ifPresent(active -> {
                    LocalDateTime created = Optional.ofNullable(active.getCreatedAt()).orElse(now);
                    long elapsed = Duration.between(created, now).getSeconds();
                    if (elapsed < cooldownSeconds) {
                        long wait = cooldownSeconds - elapsed;
                        throw new IllegalArgumentException("인증번호 재요청은 " + wait + "초 후에 가능합니다.");
                    }
                });

        String token = generateNumericToken(tokenLength);
        EmailVerification ev = EmailVerification.builder()
                .email(email)
                .token(token)
                .purpose(VerificationPurpose.RESET_PW)
                .expiresAt(now.plusSeconds(expSeconds))
                .usedAt(null)
                .build();
        evRepository.save(ev);

        String subject = "[Matcha-Talk] 비밀번호 재설정 인증번호";
        String html = """
            <h3>비밀번호 재설정 인증번호</h3>
            <p>인증번호: <b style="font-size:20px">%s</b></p>
            <p>유효시간: %d초</p>
            """.formatted(token, expSeconds);
        emailSender.send(email, subject, html);

        boolean isMock = env.matchesProfiles("mock");
        return isMock
                ? Map.of("sent", true, "dev_token", token, "expires_in_seconds", expSeconds)
                : Map.of("sent", true, "expires_in_seconds", expSeconds);
    }

    /** (B-2) 인증번호 검증 + 새 비밀번호 저장(원샷) */
    @Transactional
    public Map<String, Object> confirmResetAndChangePassword(String email, String token, String rawNewPassword) {
        email = trim(email).toLowerCase();
        token = trim(token);
        rawNewPassword = trim(rawNewPassword);

        LocalDateTime now = LocalDateTime.now();

        // 토큰 형식 검사: 숫자 tokenLength 자리
        if (!tokenPattern().matcher(token).matches()) {
            throw new IllegalArgumentException("인증번호 형식이 올바르지 않습니다.");
        }
        // 비밀번호 정책 검사
        if (!STRONG_PW.matcher(rawNewPassword).matches()) {
            throw new IllegalArgumentException("비밀번호는 8~64자이며 영문 대/소문자·숫자·특수문자를 모두 포함해야 합니다.");
        }

        EmailVerification ev = evRepository
                .findByTokenAndEmail(token, email, VerificationPurpose.RESET_PW)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다."));

        if (ev.getUsedAt() != null) {
            throw new IllegalArgumentException("이미 사용된 인증번호입니다.");
        }
        if (!ev.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다.");
        }

        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 없습니다."));

        // 비번 변경 + 잠금 해제/카운트 리셋
        user.setPasswordHash(passwordEncoder.encode(rawNewPassword));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 토큰 1회용 처리
        ev.setUsedAt(now);
        evRepository.save(ev);

        return Map.of(
                "reset", true,
                "message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인하세요."
        );
    }

    // =========================================================
    // (C) 만료 토큰 정리
    // =========================================================
    @Transactional
    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpired() {
        evRepository.deleteExpired(LocalDateTime.now());
    }

    // 회원가입 시 이메일/토큰 조합 확인용
    public void verifyTokenForEmail(String email, String token) {
        email = trim(email).toLowerCase();
        token = trim(token);
        LocalDateTime now = LocalDateTime.now();

        EmailVerification ev = evRepository.findByTokenAndEmail(token, email, VerificationPurpose.VERIFY_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다."));

        if (ev.getUsedAt() == null) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }
        if (!ev.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다.");
        }
    }
}
