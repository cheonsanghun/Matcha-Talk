package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.RoomMessage;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ChatMessageResponseDto {
    private final Long messageId;
    private final Long roomId;
    private final RoomMessage.ContentType contentType;
    private final String senderNickName;
    private final String content;
    private final String translatedContent;
    private final String fileName;
    private final String fileUrl;
    private final String mimeType;
    private final Long fileSize;
    private final LocalDateTime sentAt;
}
