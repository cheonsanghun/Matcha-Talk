package net.datasa.project01.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.UserSummary;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 로그인 검증 로직 + "5회 이상 실패 시 10분 잠금" 정책
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;   // mock/db 공용 저장소
    private final PasswordEncoder passwordEncoder; // BCrypt

    // === 잠금 정책 (원하면 properties로 뺄 수 있음) ===
    private static final int  LOCK_THRESHOLD = 5;   // 연속 5회 실패
    private static final long LOCK_MINUTES   = 10;  // 10분 잠금

    @Override
    public UserSummary loginLocal(String loginId, String rawPassword) {
        // 0) 사용자 조회 (아이디/비번 노출 방지: 동일한 문구 사용)
        User u = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        // 1) 사용 가능/잠금 상태 선체크
        if (!u.isEnabled()) {
            // enabled 는 primitive boolean 이라 Lombok이 isEnabled() 생성됨
            throw new IllegalArgumentException("비활성화된 계정입니다. 관리자에게 문의하세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            // 잠김 상태면 남은 분 계산해서 안내
            long remainSec = Duration.between(now, u.getLockedUntil()).getSeconds();
            long remainMin = Math.max(0, (remainSec + 59) / 60); // 올림 처리
            throw new IllegalArgumentException("계정이 잠겨 있습니다. 약 " + remainMin + "분 후 다시 시도하세요.");
        }

        // 2) 비밀번호 검증
        boolean ok = passwordEncoder.matches(rawPassword, u.getPasswordHash());
        if (!ok) {
            // -------- 실패 처리 시작 --------
            int next = u.getFailedLoginCount() + 1; // 연속 실패 카운트 +1
            u.setFailedLoginCount(next);

            if (next >= LOCK_THRESHOLD) {
                // 임계치 도달 → 잠금 설정
                u.setLockedUntil(now.plusMinutes(LOCK_MINUTES)); // 지금부터 10분 잠금
                u.setFailedLoginCount(0); // (선택) 잠글 때 카운터 초기화
                userRepository.save(u);   // mock: Map 업데이트 / JPA: merge
                throw new IllegalArgumentException("비밀번호를 5회 이상 틀려 계정이 "
                        + LOCK_MINUTES + "분간 잠겼습니다.");
            }

            // 임계치 미만 → 카운트만 반영
            userRepository.save(u);
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
            // -------- 실패 처리 끝 --------
        }

        // 3) 성공 처리: 잠금/카운트 흔적이 있으면 초기화
        if (u.getFailedLoginCount() != 0 || u.getLockedUntil() != null) {
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
}
