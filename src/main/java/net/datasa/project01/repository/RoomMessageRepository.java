package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {

    /**
     * 특정 채팅방의 메시지를 최신순으로 페이징하여 조회
     * @param room 조회할 채팅방
     * @param pageable 페이지 번호, 페이지 크기, 정렬 방법 등의 정보를 담은 객체
     * @return 페이징된 메시지 목록
     */
    Page<RoomMessage> findByRoomOrderByCreatedAtDesc(Room room, Pageable pageable);

}
