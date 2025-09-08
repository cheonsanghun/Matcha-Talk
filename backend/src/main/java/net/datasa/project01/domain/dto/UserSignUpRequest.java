package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.*; // 입력값 검증용 어노테이션들
import lombok.*;                        // Getter/Setter 자동 생성
import java.time.LocalDate;             // 생년월일 타입

/**
 * 회원가입 요청 정보를 담는 DTO(Data Transfer Object) 클래스입니다.
 * - 클라이언트가 회원가입할 때 보내는 데이터를 담습니다.
 * - 각 필드마다 입력값 검증(빈칸, 형식 등)을 합니다.
 */
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
public class UserSignUpRequest {

    /** 로그인 아이디 (필수, 최대 30자) */
    @NotBlank // 값이 비어있으면 안 됨
    @Size(max = 30) // 최대 30자까지 허용
    private String loginId;

    /**
     * 비밀번호 (필수, 규칙 있음)
     * - 8자 이상
     * - 소문자 1개 이상
     * - 숫자 1개 이상
     * - 특수문자 1개 이상
     */
    @NotBlank // 값이 비어있으면 안 됨
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$", // 비밀번호 규칙 정규식
            message = "비밀번호는 8자 이상, 소문자/숫자/특수문자를 각각 1개 이상 포함해야 합니다."
    )
    private String password;

    /** 비밀번호 확인 (필수, password와 일치해야 함) */
    @NotBlank // 값이 비어있으면 안 됨
    private String confirmPassword;

    /** 닉네임 (필수, 최대 30자) */
    @NotBlank // 값이 비어있으면 안 됨
    @Size(max = 30) // 최대 30자까지 허용
    private String nickName;

    /** 이메일 (필수, 이메일 형식, 최대 100자) */
    @Email // 이메일 형식이어야 함
    @NotBlank // 값이 비어있으면 안 됨
    @Size(max = 100) // 최대 100자까지 허용
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$", message = "Gmail 주소만 사용 가능합니다.")
    private String email;

    /** 이메일 인증번호 (필수) */
    @NotBlank
    @Size(max = 100)
    private String verificationToken;

    /** 국가 코드 (필수, 2글자, 대문자) 예: KR, US */
    @NotBlank // 값이 비어있으면 안 됨
    @Pattern(regexp = "^[A-Z]{2}$") // 대문자 2글자만 허용
    private String countryCode;

    /** 성별 (필수, 'M' 또는 'F'만 허용) */
    @NotBlank // 값이 비어있으면 안 됨
    @Pattern(regexp = "^[MF]$") // M 또는 F만 허용
    private String gender;

    /** 생년월일 (필수, 날짜 타입) */
    @NotNull // 값이 null이면 안 됨
    private LocalDate birthDate;

    /**
     * 비밀번호와 비밀번호 확인이 같은지 검증하는 메서드입니다.
     * - @AssertTrue: true면 검증 통과, false면 에러 메시지 표시
     * - password, confirmPassword가 모두 null이 아니고, 값이 같아야 true 반환
     */
    @AssertTrue(message = "비밀번호와 비밀번호 확인이 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        if (password == null || confirmPassword == null) return false; // 둘 중 하나라도 null이면 false
        return password.equals(confirmPassword); // 값이 같으면 true, 다르면 false
    }
}