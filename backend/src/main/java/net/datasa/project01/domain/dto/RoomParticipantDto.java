package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoomParticipantDto {
    private final Long userId;
    private final String loginId;
    private final String nickName;
    private final String role;
}
