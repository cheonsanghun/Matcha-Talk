package net.datasa.project01.domain.vo; // 인증 목적 관련 클래스가 모여있는 패키지 선언

/**
 * VerificationPurpose 열거형(enum)
 * - 이메일 인증의 목적을 정의합니다.
 * - DDL CHECK와 동일한 값으로 관리됩니다.
 * - 값이 고정되어 있고, 인증 목적이 추가/변경될 때만 수정합니다.
 */
public enum VerificationPurpose { // 인증 목적을 나타내는 열거형 타입 선언

    VERIFY_EMAIL,  // 이메일 주소 본인인증 (회원가입 등에서 사용)
    FIND_ID,       // 아이디 찾기 (아이디 분실 시 사용)
    RESET_PW       // 비밀번호 재설정 (비밀번호 분실 시 사용)
}