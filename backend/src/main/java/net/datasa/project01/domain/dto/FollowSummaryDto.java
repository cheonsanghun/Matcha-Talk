package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowSummaryDto {
    private Long followRequestId;
    private Long roomId;
    private String partnerLoginId;
    private String partnerNickName;
    private LocalDateTime acceptedAt;
    private List<ChatMessageResponseDto> recentMessages;
}
