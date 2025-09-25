package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.EmailVerification;
import net.datasa.project01.domain.vo.VerificationPurpose;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증 토큰 저장소 추상화
 * - db/mock 두 구현이 이 인터페이스를 구현
 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    EmailVerification save(EmailVerification ev);

    /** 이메일+토큰+용도로 한 건 찾기 */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.token = :token AND ev.email = :email AND ev.purpose = :purpose")
    Optional<EmailVerification> findByTokenAndEmail(@Param("token") String token, @Param("email") String email, @Param("purpose") VerificationPurpose purpose);

    /** 이메일/용도 기준으로 '아직 유효하고(만료전) 미사용' 토큰 중 최신 1건 */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.email = :email AND ev.purpose = :purpose AND ev.expiresAt > :now AND ev.usedAt IS NULL ORDER BY ev.createdAt DESC")
    Optional<EmailVerification> findLatestActiveByEmail(@Param("email") String email,
                                                        @Param("purpose") VerificationPurpose purpose,
                                                        @Param("now") LocalDateTime now);

    /** 만료 토큰 일괄 삭제 */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerification ev WHERE ev.expiresAt <= :now")
    long deleteExpired(@Param("now") LocalDateTime now);
}