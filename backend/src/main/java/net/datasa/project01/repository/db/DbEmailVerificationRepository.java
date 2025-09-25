// backend/src/main/java/net/datasa/project01/repository/db/DbEmailVerificationRepository.java
package net.datasa.project01.repository.db; // DB 관련 저장소 클래스가 모여있는 패키지 선언

import jakarta.persistence.EntityManager;              // JPA 엔티티 관리 객체 import
import jakarta.persistence.PersistenceContext;        // EntityManager 주입 어노테이션 import
import net.datasa.project01.domain.entity.EmailVerification; // 이메일 인증 엔티티 import
import net.datasa.project01.domain.vo.VerificationPurpose;   // 인증 목적 enum import
import net.datasa.project01.repository.EmailVerificationRepository; // 이메일 인증 저장소 인터페이스 import
import org.springframework.context.annotation.Profile; // 특정 profile에서만 활성화할 때 사용
import org.springframework.stereotype.Repository;       // 스프링 저장소(빈)로 등록하는 어노테이션

import java.time.LocalDateTime; // 날짜/시간 타입 import
import java.util.Optional;      // Optional import

/**
 * DbEmailVerificationRepository
 * - "db" 프로필에서 활성화됨
 * - JPA EntityManager로 email_verifications 테이블 접근
 */
@Repository
@Profile("db")
public class DbEmailVerificationRepository implements EmailVerificationRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 저장(INSERT/UPDATE)
     * 엔티티의 PK 접근자가 tokenId 라는 전제(엔티티에 맞게 사용 중).
     * 만약 PK 이름이 id 라면 getTokenId()를 getId()로 바꿔라.
     */
    @Override
    public EmailVerification save(EmailVerification ev) {
        if (ev.getTokenId() == null) {  // 신규
            em.persist(ev);
            return ev;
        } else {                         // 갱신
            return em.merge(ev);
        }
    }

    /**
     * 토큰/이메일/용도 매칭 1건
     */
    @Override
    public Optional<EmailVerification> findByTokenAndEmail(String token, String email, VerificationPurpose purpose) {
        return em.createQuery("""
                select ev
                  from EmailVerification ev
                 where ev.token   = :token
                   and ev.email   = :email
                   and ev.purpose = :purpose
                """, EmailVerification.class)
                .setParameter("token", token)
                .setParameter("email", email)
                .setParameter("purpose", purpose)
                .getResultStream()
                .findFirst();
    }

    /**
     * 특정 이메일·용도의 미사용/미만료 토큰 최신 1건 (쿨다운 체크용)
     * └ 쿨다운이 '마지막 발급 시간' 기준이므로 createdAt desc로 정렬
     */
    @Override
    public Optional<EmailVerification> findLatestActiveByEmail(String email,
                                                               VerificationPurpose purpose,
                                                               LocalDateTime now) {
        return em.createQuery("""
                select ev
                  from EmailVerification ev
                 where ev.email     = :email
                   and ev.purpose   = :purpose
                   and ev.usedAt    is null
                   and ev.expiresAt > :now
                 order by ev.createdAt desc
                """, EmailVerification.class)
                .setParameter("email", email)
                .setParameter("purpose", purpose)
                .setParameter("now", now)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * 만료 토큰 일괄 삭제
     * @return 삭제된 행 수
     */
    @Override
    public long deleteExpired(LocalDateTime now) {
        return em.createQuery("""
                delete from EmailVerification ev
                 where ev.expiresAt <= :now
                """)
                .setParameter("now", now)
                .executeUpdate();
    }
}
