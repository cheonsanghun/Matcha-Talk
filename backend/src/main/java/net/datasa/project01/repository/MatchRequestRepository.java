package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // 현재 사용자가 대기열에 있는지 확인하는 메소드는 그대로 유지합니다.
    Optional<MatchRequest> findByUserAndStatus(User user, MatchRequest.MatchStatus status);

    // [수정됨] '나의 조건'에 맞는 잠재적 매칭 상대를 찾는 더 간단한 쿼리
    @Query("SELECT mr FROM MatchRequest mr JOIN FETCH mr.user u " +
            "WHERE mr.status = :status " +
            "AND u.userPid <> :myPid " +
            // 나의 희망 성별이 '모두(A)'이거나 상대방의 성별과 일치하며,
            "AND (:myChoiceGender = 'A' OR u.gender = :myChoiceGender) " +
            // 상대방의 나이가 나의 희망 나이 범위에 속함
            "AND FUNCTION('TIMESTAMPDIFF', 'YEAR', u.birthDate, CURRENT_DATE) BETWEEN :myMinAge AND :myMaxAge " +
            "ORDER BY mr.requestedAt ASC")
    List<MatchRequest> findPotentialMatches(
            @Param("myPid") Long myPid,
            @Param("myChoiceGender") String myChoiceGender,
            @Param("myMinAge") Integer myMinAge,
            @Param("myMaxAge") Integer myMaxAge,
            @Param("status") MatchRequest.MatchStatus status
    );
}