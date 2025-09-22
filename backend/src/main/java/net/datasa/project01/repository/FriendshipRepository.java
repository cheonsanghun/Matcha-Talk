package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Friendship;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    Optional<Friendship> findByFollowerAndFollowee(User follower, User followee);

    List<Friendship> findByFollower(User follower);

    void deleteByFollowerAndFollowee(User follower, User followee);
}
