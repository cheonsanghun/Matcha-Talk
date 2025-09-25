// backend/src/main/java/net/datasa/project01/repository/jpa/UserReportRepository.java
package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.UserReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    // 상태별 최신 200건 (reporter, reported 즉시 로딩)
    @EntityGraph(attributePaths = { "reporter", "reported" })
    List<UserReport> findTop200ByStatusOrderByCreatedAtDesc(String status);

    @EntityGraph(attributePaths = { "reporter", "reported" })
    Optional<UserReport> findByReportId(Long reportId);

    // ✅ 추가: "내가 올린 신고" 최신 200건 (reporter, reported 즉시 로딩)
    @EntityGraph(attributePaths = { "reporter", "reported" })
    List<UserReport> findTop200ByReporter_UserPidOrderByReportIdDesc(Long reporterPid);

}
