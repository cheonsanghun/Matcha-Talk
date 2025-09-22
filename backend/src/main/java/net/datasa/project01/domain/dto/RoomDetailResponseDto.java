package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Room;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RoomDetailResponseDto {
    private final Long roomId;
    private final Room.RoomType roomType;
    private final List<RoomParticipantDto> participants;

    public static RoomDetailResponseDto of(Room room, List<RoomParticipantDto> participants) {
        return RoomDetailResponseDto.builder()
                .roomId(room.getRoomId())
                .roomType(room.getRoomType())
                .participants(participants)
                .build();
    }
}
