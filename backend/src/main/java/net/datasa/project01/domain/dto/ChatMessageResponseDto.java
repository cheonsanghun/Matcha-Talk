package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.datasa.project01.domain.entity.RoomMessage;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ChatMessageResponseDto {
    private final Long messageId;
    private final Long roomId;
    private final String senderLoginId;
    private final String senderNickName;
    private final RoomMessage.ContentType contentType;
    private final String content;
    private final String translatedContent; // 번역된 메시지를 담을 필드
    private final String fileName;
    private final String fileUrl;
    private final String mimeType;
    private final Long fileSize;
    private final LocalDateTime sentAt;
}