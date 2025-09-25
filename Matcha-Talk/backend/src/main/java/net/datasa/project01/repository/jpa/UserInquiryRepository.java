package net.datasa.project01.repository.jpa;

import net.datasa.project01.domain.entity.UserInquiry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserInquiryRepository extends JpaRepository<UserInquiry, Long> {

    @EntityGraph(attributePaths = { "user" })
    List<UserInquiry> findTop200ByStatusOrderByCreatedAtDesc(UserInquiry.InquiryStatus status);

    @EntityGraph(attributePaths = { "user" })
    List<UserInquiry> findTop200ByUser_UserPidOrderByInquiryIdDesc(Long userPid);
}
