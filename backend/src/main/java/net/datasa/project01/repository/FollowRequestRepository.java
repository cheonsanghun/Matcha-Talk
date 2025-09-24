package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.FollowRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    Optional<FollowRequest> findTopByRoomOrderByCreatedAtDesc(Room room);

    Optional<FollowRequest> findTopByRoomAndStatusOrderByCreatedAtDesc(Room room, FollowRequest.Status status);

    Optional<FollowRequest> findByFollowRequestIdAndReceiver_LoginId(Long followRequestId, String loginId);

    @Query("select fr from FollowRequest fr " +
            "join fetch fr.room r " +
            "join fetch fr.requester req " +
            "join fetch fr.receiver rec " +
            "where fr.status = :status and (req = :user or rec = :user) " +
            "order by fr.respondedAt desc nulls last, fr.createdAt desc")
    List<FollowRequest> findByUserAndStatus(@Param("user") User user,
                                            @Param("status") FollowRequest.Status status);
}
