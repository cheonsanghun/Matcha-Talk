package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.EmailVerification;
import net.datasa.project01.domain.vo.VerificationPurpose;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 이메일 인증 토큰 저장소 추상화
 * - db/mock 두 구현이 이 인터페이스를 구현
 */
public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification ev);

    /** 토큰+용도로 한 건 찾기(고유 토큰 기준) */
    Optional<EmailVerification> findByTokenAndPurpose(String token, VerificationPurpose purpose);

    /** 사용자/용도 기준으로 '아직 유효하고(만료전) 미사용' 토큰 중 최신 1건 */
    Optional<EmailVerification> findLatestActiveByUserAndPurpose(Long userPid,
                                                                 VerificationPurpose purpose,
                                                                 LocalDateTime now);

    /** 만료 토큰 일괄 삭제 */
    long deleteExpired(LocalDateTime now);
}
