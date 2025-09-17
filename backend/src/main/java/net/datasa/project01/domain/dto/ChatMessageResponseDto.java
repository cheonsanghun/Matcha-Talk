package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatMessageResponseDto {
    private final Long roomId;
    private final String senderNickName;
    private final String content;
    private final String translatedContent; // 번역된 메시지를 담을 필드
    private final LocalDateTime sentAt;
}