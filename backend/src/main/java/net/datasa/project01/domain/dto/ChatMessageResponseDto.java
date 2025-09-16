package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.RoomMessage;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponseDto {
    private Long messageId;
    private Long roomId;
    private String senderLoginId;
    private String senderNickName;
    private RoomMessage.ContentType contentType;
    private String content;
    private String fileName;
    private String fileUrl;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime sentAt;

    public static ChatMessageResponseDto from(RoomMessage message) {
        return ChatMessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom().getRoomId())
                .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                .senderNickName(message.getSender() != null ? message.getSender().getNickName() : "시스템")
                .contentType(message.getContentType())
                .content(message.getTextContent())
                .fileName(message.getFileName())
                .fileUrl(message.getFilePath())
                .mimeType(message.getMimeType())
                .sizeBytes(message.getSizeBytes())
                .sentAt(message.getCreatedAt())
                .build();
    }
}