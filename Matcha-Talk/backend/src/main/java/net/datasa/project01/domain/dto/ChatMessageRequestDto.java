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
    private String clientMsgId; // 멱등 처리를 위한 클라이언트 메시지 ID
}
