package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder // <-- 이 어노테이션이 추가되어야 합니다.
public class ChatMessageResponseDto {
    private final Long roomId;
    private final String senderNickName;
    private final String content;
    private final String translatedContent; // 번역된 메시지를 담을 필드
    private final LocalDateTime sentAt;
}