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
    List<RoomMember> findByRoomAndLeftAtIsNull(Room room);

    boolean existsByRoomAndUserAndLeftAtIsNull(Room room, User user);

    long countByRoomAndLeftAtIsNull(Room room);

    @Query("SELECT rm.room FROM RoomMember rm " +
            "WHERE rm.user = :user1 " +
            "AND rm.room.roomType = :roomType " +
            "AND rm.leftAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM RoomMember rm2 WHERE rm2.room = rm.room AND rm2.user = :user2 AND rm2.leftAt IS NULL)")
    Optional<Room> findActiveSharedRoom(@Param("user1") User user1,
                                        @Param("user2") User user2,
                                        @Param("roomType") Room.RoomType roomType);
}
