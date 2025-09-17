package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStartResponseDto {

    public enum Status {
        MATCHED,
        QUEUED,
        ALREADY_IN_QUEUE
    }

    public enum QueueState {
        EMPTY,
        WAITING
    }

    private Status status;
    private QueueState queueState;
    private MatchFoundResponseDto match;
    private String message;
}
