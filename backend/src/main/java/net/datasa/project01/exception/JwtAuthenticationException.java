package net.datasa.project01.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * JWT 인증 과정에서 발생하는 예외를 표현하는 커스텀 {@link AuthenticationException} 구현체입니다.
 * <p>
 * 메시지를 통해 실제 실패 원인(토큰 누락, 변조, 사용자 미존재 등)을 클라이언트에 전달할 수 있도록 구성했습니다.
 */
public class JwtAuthenticationException extends AuthenticationException {

    public JwtAuthenticationException(String message) {
        super(message);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static JwtAuthenticationException missingToken() {
        return new JwtAuthenticationException("Authorization 헤더에 JWT 토큰이 없습니다.");
    }

    public static JwtAuthenticationException invalidToken() {
        return new JwtAuthenticationException("유효하지 않은 JWT 토큰입니다.");
    }

    public static JwtAuthenticationException userNotFound() {
        return new JwtAuthenticationException("토큰과 일치하는 사용자를 찾을 수 없습니다.");
    }

    public static JwtAuthenticationException unreadableToken(Throwable cause) {
        return new JwtAuthenticationException("JWT 토큰을 해석할 수 없습니다.", cause);
    }
}
