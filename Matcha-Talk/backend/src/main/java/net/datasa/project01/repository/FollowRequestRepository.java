package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.FollowRequest;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    Optional<FollowRequest> findFirstByRequesterAndReceiverOrderByCreatedAtDesc(User requester, User receiver);
}
