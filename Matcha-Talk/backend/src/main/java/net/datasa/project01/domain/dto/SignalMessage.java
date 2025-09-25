package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {
    private String type; // 신호 타입
    private String senderLoginId; // 보내는 사람 로그인 id
    private String receiverLoginId; // 받는 사람 로그인 id
    private Object data; // 실제 WebRTC 신호 데이터
}