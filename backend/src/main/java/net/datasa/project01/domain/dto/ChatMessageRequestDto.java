package net.datasa.project01.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequestDto {
    /**
     * 클라이언트에서 서버
     */
    private Long roomId;        // 메시지를 보낼 방
    private String content;     // 메시지 내용
    private String contentType; // TEXT, IMAGE, FILE
    private String fileName;
    private String fileUrl;
    private String mimeType;
    private Long sizeBytes;
}