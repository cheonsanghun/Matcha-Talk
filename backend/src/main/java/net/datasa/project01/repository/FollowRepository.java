package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerAndFollowee(User follower, User followee);

    List<Follow> findAllByFollowerAndStatus(User follower, Follow.FollowStatus status);

    List<Follow> findAllByFolloweeAndStatus(User followee, Follow.FollowStatus status);
}
