package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchStartResponseDto {

    public enum MatchState {
        MATCHED,
        WAITING,
        ALREADY_WAITING
    }

    private MatchState state;
    private Long myRequestId;
    private Long partnerRequestId;
    private Long roomId;
    private String partnerLoginId;
    private String partnerNickName;
    private Long partnerUserPid;
    private long waitingCount;
    private String message;
    private boolean shouldCreateOffer;
}
