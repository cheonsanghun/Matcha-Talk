package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO.
 * - locked: 현재 계정이 잠금 상태인지
 * - message: 안내 문구(잠금 중 남은 시간, 실패 횟수, 성공 등)
 * - token: JWT 붙일 자리(지금은 null)
 */
@Getter
@Builder
public class LoginResponse {
    private Long userPid;
    private String loginId;
    private String nickName;
    private String roleName;
    private boolean emailVerified;
    private boolean locked;
    private String message;
    private String token; // TODO: 추후 JWT 발급 시 채우기
}
