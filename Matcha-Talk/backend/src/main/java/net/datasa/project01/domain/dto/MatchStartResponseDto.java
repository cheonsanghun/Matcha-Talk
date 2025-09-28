package net.datasa.project01.domain.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.datasa.project01.domain.entity.MatchRequest;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MatchStartResponseDto {
    private final boolean matchedNow;
    private final boolean alreadyQueued;
    private final Long requestId;
    private final MatchRequest.MatchStatus status;
    private final MatchFoundResponseDto match;

    public static MatchStartResponseDto matched(MatchRequest request, MatchFoundResponseDto match) {
        return new MatchStartResponseDto(true, false, request.getRequestId(), request.getStatus(), match);
    }

    public static MatchStartResponseDto queued(MatchRequest request, boolean alreadyQueued) {
        Long requestId = request != null ? request.getRequestId() : null;
        MatchRequest.MatchStatus status = request != null ? request.getStatus() : null;
        return new MatchStartResponseDto(false, alreadyQueued, requestId, status, null);
    }
}
