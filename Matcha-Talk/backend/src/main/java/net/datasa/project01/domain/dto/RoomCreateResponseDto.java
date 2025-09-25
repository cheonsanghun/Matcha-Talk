package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.datasa.project01.domain.entity.Room;

@Getter
@AllArgsConstructor
public class RoomCreateResponseDto {

    private Long roomId;

    // Room 엔티티를 받아서 DTO를 생성하는 정적 팩토리 메소드 (생성자 대신 사용)
    public static RoomCreateResponseDto fromEntity(Room room) {
        return new RoomCreateResponseDto(room.getRoomId());
    }
}