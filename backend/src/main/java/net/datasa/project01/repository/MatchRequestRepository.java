package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    Optional<MatchRequest> findByUserAndStatus(User user, MatchRequest.MatchStatus status);

    Optional<MatchRequest> findFirstByUserAndStatusOrderByRequestedAtDesc(User user, MatchRequest.MatchStatus status);

    Optional<MatchRequest> findByRequestIdAndUser_LoginId(Long requestId, String loginId);

    @Query("select count(mr) from MatchRequest mr where mr.status = :status and mr.user <> :user")
    long countByStatusExcludingUser(@Param("status") MatchRequest.MatchStatus status,
                                    @Param("user") User user);

    List<MatchRequest> findByRoom(Room room);

    List<MatchRequest> findByRoom_RoomId(Long roomId);

    // [수정됨] '나의 조건'에 맞는 잠재적 매칭 상대를 찾는 더 간단한 쿼리
    @Query("SELECT mr FROM MatchRequest mr JOIN FETCH mr.user u " +
            "WHERE mr.status = :status " +
            "AND u.userPid <> :myPid " +
            "AND (:myChoiceGender = 'A' OR u.gender = :myChoiceGender) " +
            "AND mr.regionCode = :myRegionCode " +
            "AND u.birthDate BETWEEN :oldestBirthDate AND :youngestBirthDate " +
            "ORDER BY mr.requestedAt ASC")
    List<MatchRequest> findPotentialMatches(
            @Param("myPid") Long myPid,
            @Param("myChoiceGender") String myChoiceGender,
            @Param("myRegionCode") String myRegionCode,
            @Param("oldestBirthDate") LocalDate oldestBirthDate,
            @Param("youngestBirthDate") LocalDate youngestBirthDate,
            @Param("status") MatchRequest.MatchStatus status
    );
}

