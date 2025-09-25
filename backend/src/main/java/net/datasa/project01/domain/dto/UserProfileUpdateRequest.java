package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * UserProfileUpdateRequest
 *
 * 회원 프로필(기본 정보) 수정 요청을 위한 DTO입니다.
 * - 닉네임, 이메일, 국가코드, 성별, 생년월일 등 주요 정보를 클라이언트에서 전달받아 검증합니다.
 * - 각 필드는 적절한 유효성 검사 어노테이션(@Size, @Email, @Pattern 등)으로 입력값을 제한합니다.
 * - 컨트롤러에서 @Valid와 함께 사용하여, 잘못된 입력 시 자동으로 예외가 발생합니다.
 */
@Getter @Setter
public class UserProfileUpdateRequest {

    /**
     * 닉네임
     * - 사용자가 표시명으로 사용하는 값
     * - 1~30자 사이의 문자열만 허용
     * - 공백만 입력, 특수문자 등은 별도 비즈니스 로직에서 추가 검증 가능
     * - 예: "홍길동", "user123"
     */
    @Size(min = 1, max = 30, message = "닉네임은 1~30자여야 합니다.")
    private String nickName;

    /**
     * 이메일
     * - 회원의 이메일 주소(로그인/알림/계정복구 등에 사용)
     * - 이메일 형식(@포함) 및 최대 100자 제한
     * - 중복 여부는 서비스/DB에서 별도 체크 필요
     * - 예: "user@example.com"
     */
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    /**
     * 국가코드
     * - ISO-3166-1 alpha-2 표준의 2자리 대문자 국가코드
     * - 예: "KR"(대한민국), "US"(미국), "JP"(일본)
     * - 반드시 대문자 2글자만 허용
     */
    @Pattern(regexp = "^[A-Z]{2}$", message = "국가코드는 대문자 2자리여야 합니다.")
    private String countryCode;

    /**
     * 성별
     * - 'M': 남성, 'F': 여성, 'U': 미선택/기타
     * - 반드시 대문자 M/F/U 중 하나만 허용
     * - 예: "M", "F", "U"
     */
    @Pattern(regexp = "^[MFU]$", message = "성별은 M/F/U 중 하나여야 합니다.")
    private String gender;

    /**
     * 생년월일
     * - 사용자의 출생일(YYYY-MM-DD)
     * - LocalDate 타입으로 직렬화/역직렬화
     * - null 허용(필수 여부는 비즈니스 로직에 따라 결정)
     * - 예: 1990-01-01
     */
    private LocalDate birthDate;
}
