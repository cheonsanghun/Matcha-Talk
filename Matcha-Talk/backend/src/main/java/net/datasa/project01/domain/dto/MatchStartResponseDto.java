package net.datasa.project01.domain.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MatchStartResponseDto {
    private final boolean matchedNow;
    private final boolean alreadyQueued;
    private final MatchFoundResponseDto match;

    public static MatchStartResponseDto matched(MatchFoundResponseDto match) {
        return new MatchStartResponseDto(true, false, match);
    }

    public static MatchStartResponseDto queued(boolean alreadyQueued) {
        return new MatchStartResponseDto(false, alreadyQueued, null);
    }
}
