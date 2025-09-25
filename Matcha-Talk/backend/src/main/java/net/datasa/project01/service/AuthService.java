package net.datasa.project01.service;

import net.datasa.project01.domain.dto.LoginResponse;

/**
 * 인증 관련 비즈니스 로직을 처리하는 서비스 인터페이스
 */
public interface AuthService {

    /**
     * 로컬 로그인(ID/비밀번호)을 처리하고, 성공 시 사용자 정보와 JWT를 반환합니다.
     * @param loginId 사용자 ID
     * @param rawPassword 사용자 비밀번호
     * @return LoginResponse (사용자 정보 + JWT)
     */
    LoginResponse loginLocal(String loginId, String rawPassword);
}