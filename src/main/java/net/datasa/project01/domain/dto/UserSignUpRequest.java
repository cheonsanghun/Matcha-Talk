package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 회원 가입 요청 DTO
 * 클라이언트가 회원 가입 시 전달하는 필수 정보만 포함
 */
@Getter @Setter
public class UserSignUpRequest {

    /**
     * 로그인 아이디
     * - 최대 30자, 공백 불가
     * - DB: users.login_id (UNIQUE)
     */
    @NotBlank
    @Size(max = 30)
    private String loginId;

    /**
     * 비밀번호(원문)
     * - 8~100자, 공백 불가
     * - 서버에서 해시 처리 후 저장
     */
    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    /**
     * 닉네임(표시명)
     * - 최대 30자, 공백 불가
     * - DB: users.nick_name
     */
    @NotBlank
    @Size(max = 30)
    private String nickName;

    /**
     * 이메일 주소
     * - 이메일 형식, 최대 100자, 공백 불가
     * - DB: users.email (UNIQUE)
     */
    @Email
    @NotBlank
    @Size(max = 100)
    private String email;

    /**
     * 국적 코드
     * - ISO-3166-1 alpha-2, 대문자 2글자 (예: KR, US)
     * - DB: users.country_code
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    private String countryCode;

    /**
     * 성별
     * - 'M' 또는 'F'만 허용
     * - DB: users.gender
     */
    @NotBlank
    @Pattern(regexp = "^[MF]$")
    private String gender;

    /**
     * 생년월일
     * - LocalDate 타입, null 불가
     * - DB: users.birth_date
     */
    @NotNull
    private LocalDate birthDate;
}