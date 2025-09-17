package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class RoomListResponseDto {
    private final Long roomId;
    private final Room.RoomType roomType;
    private final int memberCount;
    private final List<String> memberNicknames;

    public static RoomListResponseDto fromEntity(Room room, List<RoomMember> members) {
        List<String> nicknames = members.stream()
                .map(member -> member.getUser().getNickName())
                .collect(Collectors.toList());

        return RoomListResponseDto.builder()
                .roomId(room.getRoomId())
                .roomType(room.getRoomType())
                .memberCount(members.size())
                .memberNicknames(nicknames)
                .build();
    }
}