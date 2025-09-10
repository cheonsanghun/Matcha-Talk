package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatMessageResponseDto {
    private Long roomId;
    private String senderNickName;
    private String content;
    private LocalDateTime sentAt;
}