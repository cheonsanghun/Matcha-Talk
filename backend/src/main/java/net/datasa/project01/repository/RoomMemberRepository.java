package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMemberId;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaRepository의 두 번째 제네릭 타입으로 엔티티의 ID 클래스인 'RoomMemberId'를 지정
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {
    java.util.List<RoomMember> findByRoom(Room room);

    java.util.Optional<RoomMember> findByRoomAndUser(Room room, User user);

    java.util.Optional<RoomMember> findByRoom_RoomIdAndUser_LoginId(Long roomId, String loginId);

    java.util.Optional<RoomMember> findByRoomAndUserAndLeftAtIsNull(Room room, User user);

    @Query("""
            SELECT CASE WHEN COUNT(rm1) > 0 THEN true ELSE false END
            FROM RoomMember rm1
            JOIN rm1.room r
            JOIN RoomMember rm2 ON rm2.room = r
            WHERE r.roomType = :roomType
              AND (r.closedAt IS NULL)
              AND rm1.user.loginId = :senderLoginId
              AND rm2.user.loginId = :receiverLoginId
              AND rm1.leftAt IS NULL
              AND rm2.leftAt IS NULL
            """)
    boolean existsActiveRoomBetweenUsers(@Param("senderLoginId") String senderLoginId,
                                         @Param("receiverLoginId") String receiverLoginId,
                                         @Param("roomType") Room.RoomType roomType);
}
