// UserRepository.java
package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long userPid);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
