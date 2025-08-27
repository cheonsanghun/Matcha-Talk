package net.datasa.project01.domain.dto;

import lombok.*;

/**
 * 로그인 성공 시 클라이언트로 내려줄 응답(예: 액세스 토큰)
 */
@Getter @AllArgsConstructor
public class AuthResponseDto {
    private String accessToken;
}