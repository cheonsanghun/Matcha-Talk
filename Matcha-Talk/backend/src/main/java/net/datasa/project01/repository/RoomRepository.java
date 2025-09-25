package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    // JpaRepository를 상속받는 것만으로 기본적인 CRUD(Create, Read, Update, Delete) 기능이 자동 구현됩니다.
    // 향후 필요한 커스텀 쿼리 메소드(예: 특정 타입의 방 찾기)를 여기에 추가할 수 있습니다.
}