package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Query("""
            SELECT r
            FROM Room r
            WHERE r.roomType = net.datasa.project01.domain.entity.Room$RoomType.PRIVATE
              AND EXISTS (
                    SELECT 1 FROM RoomMember rm1
                    WHERE rm1.room = r AND rm1.user.loginId = :loginA
              )
              AND EXISTS (
                    SELECT 1 FROM RoomMember rm2
                    WHERE rm2.room = r AND rm2.user.loginId = :loginB
              )
            """)
    java.util.Optional<Room> findPrivateRoomByMemberLogins(@Param("loginA") String loginA, @Param("loginB") String loginB);
}