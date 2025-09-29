package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.service.ChatService;

@Getter
@Builder
@AllArgsConstructor
public class RoomCleanupResponseDto {
    private final boolean deleted;
    private final boolean temporary;
    private final boolean mutualFollow;
    private final String reason;

    public static RoomCleanupResponseDto from(ChatService.RoomCleanupResult result) {
        if (result == null) {
            return RoomCleanupResponseDto.builder()
                    .deleted(false)
                    .temporary(false)
                    .mutualFollow(false)
                    .reason("UNKNOWN")
                    .build();
        }
        return RoomCleanupResponseDto.builder()
                .deleted(result.deleted())
                .temporary(result.temporary())
                .mutualFollow(result.mutualFollow())
                .reason(result.reason())
                .build();
    }
}
