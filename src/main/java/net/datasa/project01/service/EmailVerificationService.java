package net.datasa.project01.service; // 서비스 계층 클래스가 모여있는 패키지 선언

import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 어노테이션
import net.datasa.project01.domain.entity.EmailVerification; // 이메일 인증 엔티티 import
import net.datasa.project01.domain.entity.User; // 사용자 엔티티 import
import net.datasa.project01.domain.vo.VerificationPurpose; // 인증 목적 enum import
import net.datasa.project01.repository.EmailVerificationRepository; // 이메일 인증 저장소 인터페이스 import
import net.datasa.project01.repository.UserRepository; // 사용자 저장소 인터페이스 import
import net.datasa.project01.service.email.EmailSender; // 이메일 발송기 인터페이스 import
import org.springframework.beans.factory.annotation.Value; // application.properties 값 주입 어노테이션
import org.springframework.core.env.Environment; // 현재 활성화된 프로필 확인용 환경 객체
import org.springframework.scheduling.annotation.Scheduled; // 스케줄러 어노테이션
import org.springframework.stereotype.Service; // 서비스 빈 등록 어노테이션
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 처리 어노테이션

import java.security.SecureRandom; // 보안용 랜덤 숫자 생성기
import java.time.Duration; // 시간 간격 계산용 클래스
import java.time.LocalDateTime; // 날짜/시간 타입
import java.util.Map; // Map 자료구조 import
import java.util.Optional; // 값이 있을 수도, 없을 수도 있는 컨테이너 타입 import

/**
 * 이메일 인증 서비스
 * - requestVerifyEmail(email): 토큰 발급(쿨다운 적용) + 메일 발송
 * - confirmVerifyEmail(token): 토큰 검증(만료/미사용 확인) + 사용자 이메일 인증 완료
 * - purgeExpired(): 만료 토큰 정리(스케줄러)
 *
 * mock/db 프로필 모두 같은 코드로 동작:
 * - 저장/조회는 EmailVerificationRepository가 프로필에 따라 mock/db 구현으로 교체
 * - 발송은 EmailSender가 mock(로그)/db(실메일)로 교체
 */
@Service // 스프링 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 생성
public class EmailVerificationService {

    private final UserRepository userRepository; // 사용자 저장소
    private final EmailVerificationRepository evRepository; // 이메일 인증 저장소
    private final EmailSender emailSender; // 이메일 발송기
    private final Environment env; // 환경 정보(프로필 등)

    /** 숫자 토큰 길이(기본 6자리), 유효시간(초), 재요청 쿨다운(초) */
    @Value("${app.mail.token.length:6}")              private int tokenLength; // 토큰 길이
    @Value("${app.mail.token.exp-seconds:600}")       private long expSeconds; // 토큰 유효시간(초)
    @Value("${app.mail.token.cooldown-seconds:60}")   private long cooldownSeconds; // 재요청 쿨다운(초)

    private static final SecureRandom RND = new SecureRandom(); // 보안용 랜덤 숫자 생성기

    /** 0으로 시작해도 허용하는 숫자 토큰 생성기 (예: 003241) */
    private String generateNumericToken(int len) {
        int bound = (int) Math.pow(10, len); // len=6 → 1,000,000
        return String.format("%0" + len + "d", RND.nextInt(bound)); // 앞에 0을 붙여서 고정 길이 숫자 생성
    }

    /**
     * (1) 이메일 본인인증 토큰 발급
     *  - 가입된 사용자만 가능
     *  - 최근 '미사용/미만료' 토큰이 있으면 쿨다운 시간 내 재발급 차단
     *  - 새 토큰 저장 후 메일 발송
     *  - mock 프로필이면 dev_token을 응답에 포함(Postman 편의)
     */
    @Transactional // DB 트랜잭션 처리
    public Map<String, Object> requestVerifyEmail(String email) {
        // 1) 가입된 이메일인지 확인
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 아닙니다."));

        LocalDateTime now = LocalDateTime.now(); // 현재 시각

        // 2) 재요청 쿨다운: 최신 '미사용&미만료' 토큰 존재 시 대기 요구
        evRepository.findLatestActiveByUserAndPurpose(user.getUserPid(),
                        VerificationPurpose.VERIFY_EMAIL, now)
                .ifPresent(active -> { // 최근 토큰이 있으면
                    LocalDateTime created = Optional.ofNullable(active.getCreatedAt()).orElse(now); // 생성 시각
                    long elapsed = Duration.between(created, now).getSeconds(); // 경과 시간(초)
                    if (elapsed < cooldownSeconds) { // 쿨다운 미만이면
                        long wait = cooldownSeconds - elapsed; // 남은 대기 시간
                        throw new IllegalArgumentException("인증번호 재요청은 " + wait + "초 후에 가능합니다.");
                    }
                });

        // 3) 토큰 생성 & 저장
        String token = generateNumericToken(tokenLength); // 숫자 토큰 생성
        EmailVerification ev = EmailVerification.builder()
                .user(user) // 사용자 정보
                .token(token) // 토큰 값
                .purpose(VerificationPurpose.VERIFY_EMAIL) // 인증 목적
                .expiresAt(now.plusSeconds(expSeconds)) // 만료 시각
                .usedAt(null) // 미사용
                .build();
        evRepository.save(ev); // 저장소에 저장

        // 4) 메일(또는 로그) 발송
        String subject = "[Matcha-Talk] 이메일 인증번호"; // 메일 제목
        String html = """
            <h3>이메일 인증번호</h3>
            <p>인증번호: <b style="font-size:20px">%s</b></p>
            <p>유효시간: %d초</p>
            """.formatted(token, expSeconds); // 메일 본문(HTML)
        emailSender.send(email, subject, html); // 메일 발송(혹은 로그 출력)

        // 5) mock이면 dev_token을 응답에 포함
        boolean isMock = env.matchesProfiles("mock"); // mock 프로필 여부 확인
        return isMock
                ? Map.of("sent", true, "dev_token", token, "expires_in_seconds", expSeconds) // mock: 토큰 포함
                : Map.of("sent", true, "expires_in_seconds", expSeconds); // db: 토큰 미포함
    }

    /**
     * (2) 이메일 본인인증 토큰 확인
     *  - 토큰/목적 일치 조회
     *  - 이미 사용되었거나 만료된 토큰은 거부
     *  - 성공 시 사용자 emailVerified=true로 업데이트
     *  - 토큰 used_at 세팅해 재사용 차단
     */
    @Transactional // DB 트랜잭션 처리
    public Map<String, Object> confirmVerifyEmail(String token) {
        LocalDateTime now = LocalDateTime.now(); // 현재 시각

        EmailVerification ev = evRepository.findByTokenAndPurpose(token, VerificationPurpose.VERIFY_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다.")); // 토큰/목적 일치하는 인증 정보 조회

        if (ev.getUsedAt() != null) { // 이미 사용된 토큰이면
            throw new IllegalArgumentException("이미 사용된 인증번호입니다.");
        }
        if (!ev.getExpiresAt().isAfter(now)) { // 만료된 토큰이면
            throw new IllegalArgumentException("인증번호가 만료되었습니다.");
        }

        // 사용자 이메일 인증 완료 처리
        User user = ev.getUser();
        user.setEmailVerified(true); // 사용자 인증 완료
        userRepository.save(user); // 사용자 정보 저장

        // 토큰 사용 처리(재사용 차단)
        ev.setUsedAt(now); // 사용 시각 기록
        evRepository.save(ev); // 인증 정보 저장

        return Map.of("verified", true, "user_pid", user.getUserPid()); // 결과 반환
    }

    /** (3) 만료 토큰 정리: 매일 새벽 3시 */
    @Scheduled(cron = "0 0 3 * * ?") // 매일 3시 실행
    public void purgeExpired() {
        evRepository.deleteExpired(LocalDateTime.now()); // 만료된 토큰 일괄 삭제
    }
}