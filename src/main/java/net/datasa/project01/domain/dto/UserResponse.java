// UserResponse.java
package net.datasa.project01.domain.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * 회원 정보 응답 DTO
 * - 회원 조회, 상세 정보 반환 등에 사용
 * - 민감 정보(비밀번호 등)는 포함하지 않음
 */
@Getter
@Builder
public class UserResponse {

    /**
     * 회원 PK (user_pid)
     * DB: users.user_pid
     */
    private Long userPid;

    /**
     * 로그인 아이디
     * DB: users.login_id
     */
    private String loginId;

    /**
     * 닉네임(표시명)
     * DB: users.nick_name
     */
    private String nickName;

    /**
     * 이메일 주소
     * DB: users.email
     */
    private String email;

    /**
     * 국적 코드 (ISO-3166-1 alpha-2)
     * DB: users.country_code
     */
    private String countryCode;

    /**
     * 성별 ('M' 또는 'F')
     * DB: users.gender
     */
    private String gender;

    /**
     * 생년월일
     * DB: users.birth_date
     */
    private LocalDate birthDate;

    /**
     * 권한명 (예: ROLE_USER, ROLE_ADMIN)
     * DB: users.rolename
     */
    private String roleName;

    /**
     * 계정 사용 가능 여부
     * DB: users.enabled
     */
    private boolean enabled;
}