package net.datasa.project01.domain.entity; // 엔티티 클래스가 모여있는 패키지 선언

import jakarta.persistence.*; // JPA 관련 어노테이션(import)
import lombok.*; // 롬복 어노테이션(import)
import java.time.LocalDateTime; // 날짜/시간 타입(import)

/**
 * 이메일 인증 코드 정보를 저장하는 엔티티 클래스입니다.
 * - DB 테이블: email_verification_codes
 * - 이메일 인증 코드, 만료 시간, 생성 시간 등을 관리합니다.
 */
@Getter @Setter // 롬복: getter/setter 자동 생성
@Builder // 롬복: 빌더 패턴 지원
@NoArgsConstructor // 롬복: 기본 생성자 자동 생성
@AllArgsConstructor // 롬복: 모든 필드 생성자 자동 생성
@Entity // JPA 엔티티임을 명시
@Table(
        name="email_verification_codes", // 테이블명 지정
        indexes = {@Index(name="idx_email", columnList="email")} // email 컬럼에 인덱스 생성
)
public class EmailVerificationCode {
    @Id // 기본키(PK) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가(시퀀스)
    private Long id; // 인증 코드 레코드의 고유 ID

    @Column(length=100, nullable=false) // 최대 100자, null 불가
    private String email; // 인증 받을 이메일 주소

    @Column(length=10, nullable=false) // 최대 10자, null 불가
    private String code; // 인증 코드(예: 6자리 숫자)

    @Column(nullable=false) // null 불가
    private LocalDateTime expiresAt; // 인증 코드 만료 시각

    @Column(nullable=false) // null 불가
    private LocalDateTime createdAt; // 인증 코드 생성 시각
}