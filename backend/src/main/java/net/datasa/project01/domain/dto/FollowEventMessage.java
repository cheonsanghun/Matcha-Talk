package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowEventMessage {

    public enum EventType {
        REQUESTED,
        ACCEPTED,
        DECLINED,
        CANCELLED
    }

    private EventType eventType;
    private FollowResponseDto follow;
    private String message;
}
