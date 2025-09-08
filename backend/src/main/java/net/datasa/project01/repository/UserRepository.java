package net.datasa.project01.repository; // 리포지토리 클래스가 모여있는 패키지 선언

import net.datasa.project01.domain.entity.User; // User 엔티티 import
import java.util.Optional; // 값이 있을 수도, 없을 수도 있는 타입

/**
 * User 엔티티를 위한 리포지토리 인터페이스입니다.
 * - DB에서 사용자 정보 저장, 조회, 존재 여부 확인 등의 기능을 제공합니다.
 */
public interface UserRepository {
    /**
     * 사용자 정보를 저장합니다(신규 또는 수정).
     * @param user 저장할 사용자 엔티티
     * @return 저장된 사용자 엔티티
     */
    User save(User user);

    /**
     * 사용자 고유 ID(PK)로 사용자 정보를 조회합니다.
     * @param userPid 사용자 고유 ID
     * @return 조회된 사용자(Optional, 없으면 empty)
     */
    Optional<User> findById(Long userPid);

    /**
     * 로그인 ID로 사용자 정보를 조회합니다.
     * @param loginId 로그인 ID
     * @return 조회된 사용자(Optional, 없으면 empty)
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * 이메일로 사용자 정보를 조회합니다.
     * @param email 이메일 주소
     * @return 조회된 사용자(Optional, 없으면 empty)
     */
    Optional<User> findByEmail(String email); // ★ 추가

    /**
     * 해당 로그인 ID가 이미 존재하는지 확인합니다.
     * @param loginId 로그인 ID
     * @return 존재하면 true, 아니면 false
     */
    boolean existsByLoginId(String loginId);

    /**
     * 해당 이메일이 이미 존재하는지 확인합니다.
     * @param email 이메일 주소
     * @return 존재하면 true, 아니면 false
     */
    boolean existsByEmail(String email);
}