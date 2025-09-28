package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.MatchRequest;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MatchFoundResponseDto {
    private final Long myRequestId;
    private final Long partnerRequestId;
    private final String partnerLoginId;
    private final String partnerNickName;
    private final Long roomId;
    private final String handshakeKey;
    private final LocalDateTime expiresAt;
    private final MatchRequest.MatchStatus status;
    // 향후 파트너의 프로필 사진, 관심사 등 추가 정보 포함 가능
}