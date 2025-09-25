// src/main/java/net/datasa/project01/service/AuthServiceImpl.java
package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 로그인 검증 로직 + "5회 이상 실패 시 10분 잠금" 정책
 * - 아이디 또는 이메일 + 비밀번호로 로그인 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional // 클래스 기본 트랜잭션 유지
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;   // db 공용 저장소
    private final PasswordEncoder passwordEncoder; // BCrypt

    private static final int  LOCK_THRESHOLD = 5;  // 연속 5회 실패
    private static final long LOCK_MINUTES   = 10; // 10분 잠금

    /**
     * 로그인 시도.
     * 실패 시에도 실패카운트/잠금은 반드시 커밋되어야 하므로
     * AuthException에 대해 롤백하지 않도록 설정한다.
     *
     * @param identifier 아이디 또는 이메일
     * @param rawPassword 평문 비밀번호
     */
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public UserSummary loginLocal(String identifier, String rawPassword) {
        // 0) 사용자 조회 (아이디/이메일 분기)
        final boolean isEmail = identifier != null && identifier.contains("@");

        User u = (isEmail
                ? userRepository.findByEmail(identifier)
                : userRepository.findByLoginId(identifier))
                .orElseThrow(() -> new AuthException(401, "아이디 또는 비밀번호가 올바르지 않습니다."));

        // 디버그용
        log.info("loginLocal identifier={}, path={}", identifier, (isEmail ? "EMAIL" : "LOGIN_ID"));

        // 1) 사용 가능/잠금 상태 선체크
        if (!u.isEnabled()) {
            throw new AuthException(403, "비활성화된 계정입니다. 관리자에게 문의하세요.");
        }
        final LocalDateTime now = LocalDateTime.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            long remainSec = Math.max(0, Duration.between(now, u.getLockedUntil()).getSeconds());
            // 423 Locked + 남은초를 메시지에 담아 프런트에서 카운트다운 가능
            throw new AuthException(
                    423,
                    "ACCOUNT_LOCKED: 계정이 잠겨 있습니다. remainingSeconds=" + remainSec
            );
        }

        // 2) 비밀번호 검증
        boolean ok = passwordEncoder.matches(rawPassword, u.getPasswordHash());
        if (!ok) {
            // -------- 실패 처리 시작 --------
            int current = safeInt(u.getFailedLoginCount());
            int next    = current + 1;
            u.setFailedLoginCount(next);

            // 임계 도달 → 잠금 설정
            if (next >= LOCK_THRESHOLD) {
                LocalDateTime until = now.plusMinutes(LOCK_MINUTES);
                u.setLockedUntil(until);
                // (선택) 잠글 때 실패 카운트 리셋
                u.setFailedLoginCount(0);
                userRepository.save(u);

                long remainSec = Math.max(0, Duration.between(now, until).getSeconds());
                // 423 Locked 로 응답, 남은초 포함
                throw new AuthException(
                        423,
                        "ACCOUNT_LOCKED: 비밀번호를 " + LOCK_THRESHOLD + "회 이상 틀려 계정이 "
                                + LOCK_MINUTES + "분간 잠겼습니다. remainingSeconds=" + remainSec
                );
            }

            // 아직 잠기지 않음 → 남은 시도 안내(401)
            userRepository.save(u);
            int remainAttempts = Math.max(0, LOCK_THRESHOLD - next);
            throw new AuthException(
                    401,
                    "BAD_CREDENTIALS: 아이디 또는 비밀번호가 올바르지 않습니다. remainingAttempts=" + remainAttempts
            );
            // -------- 실패 처리 끝 --------
        }

        // 3) 성공 처리: 흔적 초기화
        if (safeInt(u.getFailedLoginCount()) != 0 || u.getLockedUntil() != null) {
            u.setFailedLoginCount(0);
            u.setLockedUntil(null);
            userRepository.save(u);
        }

        // 4) 안전한 요약으로 응답 (민감정보 제외)
        return UserSummary.builder()
                .id(u.getUserPid())
                .loginId(u.getLoginId())
                .nickname(u.getNickName())
                .email(u.getEmail())
                .build();
    }

    /** null 안전 정수 변환 */
    private int safeInt(Integer i) {
        return i == null ? 0 : i;
    }
}
