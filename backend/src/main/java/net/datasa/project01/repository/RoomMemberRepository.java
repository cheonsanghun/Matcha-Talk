package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository의 두 번째 제네릭 타입으로 엔티티의 ID 클래스인 'RoomMemberId'를 지정
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    boolean existsByRoom_RoomIdAndUser_UserPidAndLeftAtIsNull(Long roomId, Long userPid);
}
