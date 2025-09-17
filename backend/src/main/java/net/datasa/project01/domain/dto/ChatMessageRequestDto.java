package net.datasa.project01.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequestDto {
    /**
     * 클라이언트에서 서버로 전달되는 채팅 메시지 요청 DTO
     */
    private Long roomId;        // 메시지를 보낼 방
    private String content;     // 메시지 내용
    private boolean translate;  // 번역 요청 여부
}