package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder // <-- 이 어노테이션이 추가되어야 합니다.
public class ChatMessageResponseDto {
    private final Long messageId;
    private final Long roomId;
    private final String senderLoginId;
    private final String senderNickName;
    private final String senderLanguageCode; // 발신자의 언어 코드
    private final String content;
    private final String contentType;
    private final String fileName;
    private final String fileUrl;
    private final String mimeType;
    private final Long sizeBytes;
    private final LocalDateTime sentAt;
}