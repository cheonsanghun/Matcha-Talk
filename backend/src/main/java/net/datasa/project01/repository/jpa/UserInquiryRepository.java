// backend/src/main/java/net/datasa/project01/repository/jpa/UserInquiryRepository.java
package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.UserInquiry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserInquiryRepository extends JpaRepository<UserInquiry, Long> {

    /** 상태별 최신 200건 (user 즉시 로딩) */
    @EntityGraph(attributePaths = { "user" })
    List<UserInquiry> findTop200ByStatusOrderByCreatedAtDesc(String status);

    /** 사용자별 최신 200건 (user 즉시 로딩) */
    @EntityGraph(attributePaths = { "user" })
    List<UserInquiry> findTop200ByUser_UserPidOrderByInquiryIdDesc(Long userPid);
}
