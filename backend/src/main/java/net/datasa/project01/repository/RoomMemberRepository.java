package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMemberId;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// JpaRepository의 두 번째 제네릭 타입으로 엔티티의 ID 클래스인 'RoomMemberId'를 지정
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {
    List<RoomMember> findByRoom(Room room);

    List<RoomMember> findByUser(User user);

    List<RoomMember> findByUserLoginId(String loginId);

    boolean existsByRoomAndUser(Room room, User user);
}
