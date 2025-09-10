package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.RoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository의 두 번째 제네릭 타입으로 엔티티의 ID 클래스인 'RoomMemberId'를 지정
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {
    // 예시: 특정 방의 모든 참여자를 찾는 커스텀 메소드
    // List<RoomMember> findByRoom(Room room);
}
