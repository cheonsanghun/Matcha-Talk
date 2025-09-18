package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMemberId;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

// JpaRepository의 두 번째 제네릭 타입으로 엔티티의 ID 클래스인 'RoomMemberId'를 지정
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    /** 사용자가 참여하고 있는 모든 채팅방 멤버 정보를 조회 */
    List<RoomMember> findByUser(User user);

    /** 특정 채팅방에 속한 모든 멤버 정보를 조회 */
    List<RoomMember> findByRoom(Room room);

    /** 특정 채팅방에 특정 사용자가 멤버로 있는지 확인 (권한 검사용) */
    Optional<RoomMember> findByRoomAndUser(Room room, User user);

    /**
     * 특정 사용자가 속한 모든 채팅방과 그 방의 모든 멤버 정보를 한 번의 쿼리로 조회 (N+1 문제 해결)
     */
    @Query("SELECT rm FROM RoomMember rm " +
           "JOIN FETCH rm.room r " +
           "WHERE r IN (SELECT rm2.room FROM RoomMember rm2 WHERE rm2.user = :user)")
    List<RoomMember> findAllRoomsAndMembersByUser(@Param("user") User user);
}
