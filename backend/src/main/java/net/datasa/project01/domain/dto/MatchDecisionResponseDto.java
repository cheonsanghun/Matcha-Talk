package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.MatchRequest;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchDecisionResponseDto {

    public enum Decision {
        ACCEPTED,
        DECLINED
    }

    private Decision decision;
    private Long roomId;
    private Long myRequestId;
    private Long partnerRequestId;
    private MatchRequest.MatchStatus myStatus;
    private MatchRequest.MatchStatus partnerStatus;
    private boolean bothAccepted;
    private boolean shouldCreateOffer;
    private String message;
}
