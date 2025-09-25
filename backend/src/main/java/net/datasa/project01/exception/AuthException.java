package net.datasa.project01.exception;

/**
 * 인증/인가 관련 사용자 피드백을 담는 런타임 예외
 * - GlobalExceptionHandler에서 잡아 사용자에게 상태코드+메시지로 내려줌
 * - @Transactional(noRollbackFor=AuthException.class)와 함께 사용하여
 *   예외가 발생해도 DB 업데이트(실패카운트/잠금)가 커밋되도록 함
 */
public class AuthException extends RuntimeException {
    private final int status;

    public AuthException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int getStatus() {
        return status;
    }
}
