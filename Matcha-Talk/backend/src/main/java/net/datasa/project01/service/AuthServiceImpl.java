package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.LoginResponse;
import net.datasa.project01.domain.dto.UserSummary;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.exception.AuthException;
import net.datasa.project01.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 로그인 검증 로직 + "5회 이상 실패 시 10분 잠금" 정책.
 * - 아이디 또는 이메일(특히 Gmail) 기반 로그인 지원.
 * - 오류 케이스는 AuthException으로 포장해 상태코드를 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final int  LOCK_THRESHOLD = 5;   // 연속 실패 허용 횟수
    private static final long LOCK_MINUTES   = 10;  // 잠금 유지 시간(분)

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse loginLocal(String identifier, String rawPassword) {
        final String id = identifier == null ? "" : identifier.trim();
        final boolean loginByEmail = id.contains("@");

        User user = (loginByEmail
                ? userRepository.findByEmail(id.toLowerCase())
                : userRepository.findByLoginId(id))
                .orElseThrow(() -> new AuthException(401, "BAD_CREDENTIALS: 아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!user.isEnabled()) {
            throw new AuthException(403, "비활성화된 계정입니다. 관리자에게 문의하세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long remainSec = Math.max(0, Duration.between(now, user.getLockedUntil()).getSeconds());
            throw new AuthException(423,
                    "ACCOUNT_LOCKED: 계정이 잠겨 있습니다. remainingSeconds=" + remainSec);
        }

        boolean matches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!matches) {
            int current = safeInt(user.getFailedLoginCount());
            int next = current + 1;
            user.setFailedLoginCount(next);

            if (next >= LOCK_THRESHOLD) {
                LocalDateTime until = now.plusMinutes(LOCK_MINUTES);
                user.setLockedUntil(until);
                user.setFailedLoginCount(0);
                userRepository.save(user);
                long remainSec = Math.max(0, Duration.between(now, until).getSeconds());
                throw new AuthException(423,
                        "ACCOUNT_LOCKED: 비밀번호를 " + LOCK_THRESHOLD + "회 이상 틀려 계정이 "
                                + LOCK_MINUTES + "분간 잠겼습니다. remainingSeconds=" + remainSec);
            }

            userRepository.save(user);
            int remainAttempts = Math.max(0, LOCK_THRESHOLD - next);
            throw new AuthException(401,
                    "BAD_CREDENTIALS: 아이디 또는 비밀번호가 올바르지 않습니다. remainingAttempts=" + remainAttempts);
        }

        if (safeInt(user.getFailedLoginCount()) != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        UserSummary summary = UserSummary.fromEntity(user);
        return new LoginResponse(summary);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
