package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 성공 시 프론트엔드로 반환되는 응답 모델.
 *
 * <p>액세스 토큰(JWT)과 화면에 필요한 최소한의 사용자 요약 정보를 함께 전달한다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT 액세스 토큰 */
    private String token;

    /** 토큰 타입 (예: Bearer) */
    private String tokenType;

    /** 화면 상태 유지를 위한 사용자 요약 정보 */
    private UserSummary user;
}
