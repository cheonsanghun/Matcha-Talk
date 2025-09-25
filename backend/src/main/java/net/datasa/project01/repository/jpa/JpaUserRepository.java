// src/main/java/net/datasa/project01/repository/jpa/JpaUserRepository.java
package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA 기반의 UserRepository 구현체.
 * - Spring Data JPA를 활용하여 User 엔티티의 CRUD 및 조회 기능을 제공합니다.
 * - @Repository: 스프링 빈으로 등록되어 예외 변환 등 Repository 역할을 수행합니다.
 * - @Profile("db"): 'db' 프로파일이 활성화된 환경에서만 빈으로 등록됩니다.
 */
@Repository
@Profile("db")
public class JpaUserRepository implements UserRepository {

    /**
     * Spring Data JPA가 자동 생성하는 User JPA Repository
     * - 실제 DB 접근 및 쿼리 실행을 담당합니다.
     * - 생성자를 통해 주입받습니다.
     */
    private final SpringDataUserJpaRepository jpa;

    /**
     * 생성자 주입 방식으로 SpringDataUserJpaRepository를 할당합니다.
     * - 스프링이 자동으로 빈을 주입합니다.
     */
    public JpaUserRepository(SpringDataUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * User 엔티티 저장(신규/수정)
     * - jpa.save(user): 신규면 insert, 기존이면 update
     */
    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    /**
     * PK(id)로 User 조회
     * - Optional로 감싸 null 안전성 제공
     */
    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id);
    }

    /**
     * 로그인 ID로 User 조회
     * - 로그인 시 주로 사용
     */
    @Override
    public Optional<User> findByLoginId(String loginId) {
        return jpa.findByLoginId(loginId);
    }

    /**
     * 이메일로 User 조회
     * - 비밀번호 찾기 등에서 사용
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    /**
     * 로그인 ID 존재 여부 확인
     * - 회원가입 시 중복 체크 등에 사용
     */
    @Override
    public boolean existsByLoginId(String loginId) {
        return jpa.existsByLoginId(loginId);
    }

    /**
     * 이메일 존재 여부 확인
     * - 회원가입 시 중복 체크 등에 사용
     */
    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }
}