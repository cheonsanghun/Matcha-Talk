package net.datasa.project01.repository;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Query("""
            SELECT mr
            FROM MatchRequest mr
            JOIN FETCH mr.user u
            WHERE mr.status = :status
              AND u.userPid <> :myPid
              AND (
                   :myChoiceGender = net.datasa.project01.domain.entity.MatchRequest.Gender.A
                   OR u.gender = :myChoiceGender
              )
              AND function('timestampdiff', YEAR, u.birthDate, current_date)
                  BETWEEN :myMinAge AND :myMaxAge
            ORDER BY mr.requestedAt ASC
            """)
    List<MatchRequest> findPotentialMatches(
            @Param("myPid") Long myPid,
            @Param("myChoiceGender") MatchRequest.Gender myChoiceGender,
            @Param("myMinAge") Integer myMinAge,
            @Param("myMaxAge") Integer myMaxAge,
            @Param("status") MatchRequest.MatchStatus status
    );

}