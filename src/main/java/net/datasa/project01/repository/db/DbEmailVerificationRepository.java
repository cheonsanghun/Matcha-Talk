package net.datasa.project01.repository.db; // DB 관련 저장소 클래스가 모여있는 패키지 선언

import jakarta.persistence.EntityManager; // JPA 엔티티 관리 객체 import
import jakarta.persistence.PersistenceContext; // EntityManager 주입 어노테이션 import
import net.datasa.project01.domain.entity.EmailVerification; // 이메일 인증 엔티티 import
import net.datasa.project01.domain.vo.VerificationPurpose; // 인증 목적 enum import
import net.datasa.project01.repository.EmailVerificationRepository; // 이메일 인증 저장소 인터페이스 import
import org.springframework.context.annotation.Profile; // 특정 profile에서만 활성화할 때 사용
import org.springframework.stereotype.Repository; // 스프링 저장소(빈)로 등록하는 어노테이션

import java.time.LocalDateTime; // 날짜/시간 타입 import
import java.util.Optional; // 값이 있을 수도, 없을 수도 있는 컨테이너 타입 import

/**
 * DbEmailVerificationRepository 클래스
 * - "db" 프로필에서 활성화됨
 * - JPA의 EntityManager를 사용해 email_verifications 테이블을 직접 접근하는 구현체
 */
@Repository // 스프링이 관리하는 저장소(빈)로 등록
@Profile("db") // "db" 프로필일 때만 활성화됨
public class DbEmailVerificationRepository implements EmailVerificationRepository { // 이메일 인증 저장소 인터페이스 구현

    @PersistenceContext // EntityManager를 스프링이 주입해줌
    private EntityManager em; // JPA 엔티티 관리 객체

    /**
     * 이메일 인증 정보 저장 (INSERT 또는 UPDATE)
     * @param ev 저장할 EmailVerification 엔티티
     * @return 저장된 엔티티
     */
    @Override
    public EmailVerification save(EmailVerification ev) {
        if (ev.getTokenId() == null) { // 신규 데이터면
            em.persist(ev);   // DB에 INSERT
            return ev;
        } else { // 기존 데이터면
            return em.merge(ev); // DB에 UPDATE
        }
    }

    /**
     * 토큰과 용도로 인증 정보 조회
     * @param token 인증 토큰 값
     * @param purpose 인증 목적
     * @return 조회된 EmailVerification (없으면 Optional.empty)
     */
    @Override
    public Optional<EmailVerification> findByTokenAndPurpose(String token, VerificationPurpose purpose) {
        return em.createQuery("""
            select ev
              from EmailVerification ev
             where ev.token = :token
               and ev.purpose = :purpose
            """, EmailVerification.class) // JPQL 쿼리 작성
                .setParameter("token", token) // 토큰 값 바인딩
                .setParameter("purpose", purpose) // 인증 목적 바인딩
                .getResultStream() // 결과를 스트림으로 받음
                .findFirst(); // 첫 번째 결과만 반환 (없으면 empty)
    }

    /**
     * 특정 사용자와 용도에 대해, 아직 사용되지 않고 만료되지 않은 최신 인증 정보 조회
     * @param userPid 사용자 PK
     * @param purpose 인증 목적
     * @param now 현재 시각
     * @return 조회된 EmailVerification (없으면 Optional.empty)
     */
    @Override
    public Optional<EmailVerification> findLatestActiveByUserAndPurpose(Long userPid,
                                                                        VerificationPurpose purpose,
                                                                        LocalDateTime now) {
        return em.createQuery("""
            select ev
              from EmailVerification ev
             where ev.user.userPid = :userPid
               and ev.purpose = :purpose
               and ev.usedAt is null
               and ev.expiresAt > :now
             order by ev.expiresAt desc
            """, EmailVerification.class) // JPQL 쿼리 작성
                .setParameter("userPid", userPid) // 사용자 PK 바인딩
                .setParameter("purpose", purpose) // 인증 목적 바인딩
                .setParameter("now", now) // 현재 시각 바인딩
                .setMaxResults(1) // 1개만 조회
                .getResultStream() // 결과를 스트림으로 받음
                .findFirst(); // 첫 번째 결과만 반환 (없으면 empty)
    }

    /**
     * 만료된 인증 정보 삭제
     * @param now 기준 시각
     * @return 삭제된 행 수
     */
    @Override
    public long deleteExpired(LocalDateTime now) {
        return em.createQuery("""
            delete from EmailVerification ev
             where ev.expiresAt <= :now
            """) // JPQL 삭제 쿼리 작성
                .setParameter("now", now) // 기준 시각 바인딩
                .executeUpdate(); // 삭제 실행, 삭제된 행 수 반환
    }
}