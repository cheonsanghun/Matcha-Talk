package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(String loginId);

    // 이메일 중복 체크를 위한 메소드 추가
    Optional<User> findByEmail(String email);
}
