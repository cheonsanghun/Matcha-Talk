package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerAndFollowee(User follower, User followee);

    List<Follow> findByFollowerAndStatus(User follower, Follow.Status status);

    List<Follow> findByFolloweeAndStatus(User followee, Follow.Status status);
}
