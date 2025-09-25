package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.UserPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * UserPenalty 엔티티용 Spring Data JPA Repository 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD, 페이징, 정렬 기능을 자동 제공.
 * - @Repository: 스프링 빈으로 등록되어 예외 변환 등 Repository 역할을 수행.
 * - 메서드명 규칙에 따라 쿼리가 자동 생성됨.
 */
@Repository
public interface UserPenaltyRepository extends JpaRepository<UserPenalty, Long> {

    /**
     * 특정 사용자의 제재 이력(최근순) 조회
     * - user.userPid(사용자 PK)로 필터링
     * - 제재 시작일(startsAt) 내림차순 정렬
     * - 사용자별 제재 내역을 최신순으로 확인할 때 사용
     */
    List<UserPenalty> findByUser_UserPidOrderByStartsAtDesc(Long userPid);
}