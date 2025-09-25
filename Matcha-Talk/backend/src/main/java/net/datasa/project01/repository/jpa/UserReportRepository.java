package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.UserReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    @EntityGraph(attributePaths = { "reporter", "reported" })
    List<UserReport> findTop200ByStatusOrderByCreatedAtDesc(UserReport.ReportStatus status);

    @EntityGraph(attributePaths = { "reporter", "reported" })
    Optional<UserReport> findByReportId(Long reportId);

    @EntityGraph(attributePaths = { "reporter", "reported" })
    List<UserReport> findTop200ByReporter_UserPidOrderByReportIdDesc(Long reporterPid);
}
