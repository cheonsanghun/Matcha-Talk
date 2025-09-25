package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchFoundResponseDto {
    private Long roomId;
    private String partnerNickName;
    // 향후 파트너의 프로필 사진, 관심사 등 추가 정보 포함 가능
}