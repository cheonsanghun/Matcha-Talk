// backend/src/main/java/net/datasa/project01/repository/EmailVerificationRepository.java
package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.EmailVerification;
import net.datasa.project01.domain.vo.VerificationPurpose;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * EmailVerification 저장소 (직접 구현체 방식).
 * - DbEmailVerificationRepository (@Profile("db"))
 * - MockEmailVerificationRepository (@Profile("mock"))  // 있다면
 */
public interface EmailVerificationRepository {

    /** 저장(INSERT/UPDATE) */
    EmailVerification save(EmailVerification ev);

    /** 토큰/이메일/용도 매칭 1건 */
    Optional<EmailVerification> findByTokenAndEmail(String token, String email, VerificationPurpose purpose);

    /** 특정 이메일·용도의 미사용/미만료 토큰 최신 1건 (쿨다운 체크용) */
    Optional<EmailVerification> findLatestActiveByEmail(String email, VerificationPurpose purpose, LocalDateTime now);

    /** 만료 토큰 일괄 삭제 → 삭제된 행 수 반환 */
    long deleteExpired(LocalDateTime now);
}
