package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // 현재 사용자가 특정 상태로 존재하는지 확인
    Optional<MatchRequest> findByUserAndStatus(User user, MatchRequest.MatchStatus status);

    Optional<MatchRequest> findFirstByUserAndStatusOrderByRequestedAtDesc(User user, MatchRequest.MatchStatus status);

    @Query("SELECT mr FROM MatchRequest mr JOIN FETCH mr.user WHERE mr.requestId = :id")
    Optional<MatchRequest> findByIdWithUser(@Param("id") Long id);

    List<MatchRequest> findAllByHandshakeKey(String handshakeKey);

    List<MatchRequest> findByRoom(Room room);

    List<MatchRequest> findAllByRoomAndStatusIn(Room room, Collection<MatchRequest.MatchStatus> statuses);

    // [수정됨] '나의 조건'에 맞는 잠재적 매칭 상대를 찾는 더 간단한 쿼리
    @Query("SELECT mr FROM MatchRequest mr JOIN FETCH mr.user u " +
           "WHERE mr.status = :status " +
           "AND u.userPid <> :myPid " +
           "AND (:regionCode IS NULL OR mr.regionCode = :regionCode) " +
           "ORDER BY mr.requestedAt ASC")
    List<MatchRequest> findPotentialMatches(
            @Param("myPid") Long myPid,
            @Param("status") MatchRequest.MatchStatus status,
            @Param("regionCode") String regionCode
    );
}