package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * [DbUserRepository]
 * - 운영 환경에서 실제 DB(MySQL 등)에 회원 정보를 저장/조회하는 클래스입니다.
 * - JPA의 EntityManager를 사용하여 DB와 연동합니다.
 * - Spring의 "db" 프로필에서만 활성화됩니다.
 * - UserRepository 인터페이스를 구현하여, mock 저장소와 동일한 메서드 구조를 제공합니다.
 */
@Repository
@Profile("db") // db 프로필에서만 활성화됨
public class DbUserRepository implements UserRepository {

    @PersistenceContext
    private EntityManager em; // JPA 엔티티 매니저 (DB 연결 및 쿼리 수행)

    @Override
    public User save(User user) {
        // 회원정보 저장(신규: persist, 기존: merge)
        // userPid가 없거나 DB에 없는 경우 신규로 간주
        if (user.getUserPid() == null || em.find(User.class, user.getUserPid()) == null) {
            em.persist(user); // 신규 회원 저장
            return user;
        } else {
            return em.merge(user); // 기존 회원 정보 수정
        }
    }


    @Override
    public Optional<User> findByEmail(String email) {
        return em.createQuery("select u from User u where u.email = :email", User.class)
                .setParameter("email", email)
                .getResultList().stream().findFirst();
    }

    @Override
    public Optional<User> findById(Long userPid) {
        // 회원번호(PK)로 회원정보 조회
        return Optional.ofNullable(em.find(User.class, userPid));
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        // 로그인ID로 회원정보 조회 (JPQL 사용)
        User user = em.createQuery(
                        "SELECT u FROM User u WHERE u.loginId = :loginId", User.class)
                .setParameter("loginId", loginId)
                .getResultList().stream().findFirst().orElse(null);
        return Optional.ofNullable(user);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        // 로그인ID 중복 체크 (COUNT 쿼리)
        Long count = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.loginId = :loginId", Long.class)
                .setParameter("loginId", loginId)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        // 이메일 중복 체크 (COUNT 쿼리)
        Long count = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }
}