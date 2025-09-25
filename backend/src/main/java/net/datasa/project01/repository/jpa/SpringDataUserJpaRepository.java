// src/main/java/net/datasa/project01/repository/jpa/SpringDataUserJpaRepository.java
package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 기반의 User 엔티티용 Repository 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD 및 페이징/정렬 기능을 자동 제공.
 * - @Repository: 스프링 빈으로 등록되어 예외 변환 등 Repository 역할을 수행.
 * - 메서드명 규칙에 따라 쿼리가 자동 생성됨.
 */
@Repository
public interface SpringDataUserJpaRepository extends JpaRepository<User, Long> {

    /**
     * 로그인 ID로 User 조회
     * - Optional로 감싸 null 안전성 제공
     * - 메서드명 기반 쿼리 자동 생성
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * 이메일로 User 조회
     * - Optional로 감싸 null 안전성 제공
     */
    Optional<User> findByEmail(String email);

    /**
     * 로그인 ID 존재 여부 확인
     * - 회원가입 시 중복 체크 등에 사용
     */
    boolean existsByLoginId(String loginId);

    /**
     * 이메일 존재 여부 확인
     * - 회원가입 시 중복 체크 등에 사용
     */
    boolean existsByEmail(String email);

    /**
     * 관리자 화면: 아이디/이메일 부분 검색(최신순)용
     * - loginId 또는 email에 입력값이 포함된 사용자 최대 100명 조회
     * - 대소문자 구분 없이 검색, 생성일 내림차순 정렬
     * - 검색 결과가 많을 때 성능 최적화 목적
     */
    List<User> findTop100ByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
            String loginIdPart, String emailPart
    );
}