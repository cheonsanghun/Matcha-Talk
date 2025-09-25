package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder // <-- 이 어노테이션이 추가되어야 합니다.
public class ChatMessageResponseDto {
    private final Long roomId;
    private final String senderNickName;
    private final String senderLanguageCode; // 발신자의 언어 코드
    private final String content;
    private final LocalDateTime sentAt;
}