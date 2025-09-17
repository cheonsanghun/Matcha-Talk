package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchEventMessage {

    public enum EventType {
        MATCH_FOUND,
        PARTNER_ACCEPTED,
        PARTNER_DECLINED,
        BOTH_CONFIRMED,
        MATCH_CANCELLED
    }

    private EventType eventType;
    private Long roomId;
    private Long myRequestId;
    private Long partnerRequestId;
    private String partnerLoginId;
    private String partnerNickName;
    private Long partnerUserPid;
    private String message;
    private boolean shouldCreateOffer;
}
