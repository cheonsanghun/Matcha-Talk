package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class RoomDetailResponseDto {
    private final Long roomId;
    private final Room.RoomType roomType;
    private final int capacity;
    private final List<ParticipantDto> participants;

    @Getter
    @Builder
    public static class ParticipantDto {
        private final String loginId;
        private final String nickname;
        private final String role;
    }

    public static RoomDetailResponseDto fromEntity(Room room, List<RoomMember> members) {
        List<ParticipantDto> participantDtos = members.stream()
                .map(member -> ParticipantDto.builder()
                        .loginId(member.getUser().getLoginId())
                        .nickname(member.getUser().getNickName())
                        .role(member.getRole())
                        .build())
                .collect(Collectors.toList());

        return RoomDetailResponseDto.builder()
                .roomId(room.getRoomId())
                .roomType(room.getRoomType())
                .capacity(room.getCapacity())
                .participants(participantDtos)
                .build();
    }
}