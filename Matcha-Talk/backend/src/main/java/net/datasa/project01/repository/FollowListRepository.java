package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.FollowList;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowListRepository extends JpaRepository<FollowList, Long> {

    Optional<FollowList> findByOwnerAndTargetAndDirection(User owner, User target, FollowList.FollowDirection direction);
}
