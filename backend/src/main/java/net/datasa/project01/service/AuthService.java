package net.datasa.project01.service;

import net.datasa.project01.domain.dto.LoginResponse;

/**
 * 로그인 기능 "계약" (저장소가 mock이든 JPA든 동일 규칙)
 */
public interface AuthService {

    /**
     * 로컬(ID/비번) 로그인
     * @param loginId 아이디
     * @param rawPassword 평문 비밀번호
     * @return 로그인 성공한 User의 안전한 요약
     * @throws IllegalArgumentException 인증 실패/잠금/비활성 등
     */
    LoginResponse loginLocal(String loginId, String rawPassword);
}
