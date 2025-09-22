package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.FollowList;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowListRepository extends JpaRepository<FollowList, Long> {
    List<FollowList> findByFollow(Follow follow);

    List<FollowList> findByOwnerAndDirectionOrderByCreatedAtDesc(User owner, FollowList.Direction direction);

    List<FollowList> findByOwnerAndDirectionAndStatusOrderByCreatedAtDesc(
            User owner,
            FollowList.Direction direction,
            Follow.Status status
    );
}
