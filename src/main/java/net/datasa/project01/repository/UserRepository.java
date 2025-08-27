package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ✅ Repository란?
 *  - "DB에 저장/조회"를 해주는 인터페이스(DAO를 자동으로 만들어주는 느낌)
 *  - JpaRepository<User, Long> 를 상속하면
 *      - save(), findById(), findAll(), deleteById() 같은 기본 메서드가 자동 제공됨
 *  - 메서드 이름 규칙(쿼리 메서드)으로 findByLoginId() 같은 것도 자동으로 구현됨
 *
 * ⚠️ 두번째 제네릭 타입(Long)은 엔티티의 PK 타입
 *    users 테이블의 PK 컬럼은 user_pid(BIGINT) → 자바에선 Long
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 로그인ID로 사용자 1명 찾기
     * - 메서드 이름에 'LoginId'를 쓰면,
     *   JPA가 'login_id = ?' 조건을 가진 쿼리를 만들어 실행함
     * - 결과가 없을 수 있으니 Optional로 감싸서 반환
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * 해당 로그인ID가 이미 존재하는지 여부만 빠르게 확인
     * - 회원가입 중복 체크에 유용
     */
    boolean existsByLoginId(String loginId);
}