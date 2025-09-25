package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    Optional<MatchRequest> findByUserAndStatus(User user, MatchRequest.MatchStatus status);

    Optional<MatchRequest> findByRequestIdAndUser_LoginId(Long requestId, String loginId);

    Optional<MatchRequest> findFirstByStatusAndUserNotOrderByRequestedAtAsc(MatchRequest.MatchStatus status, User user);

    long countByStatus(MatchRequest.MatchStatus status);

    List<MatchRequest> findByRoom(Room room);
}

