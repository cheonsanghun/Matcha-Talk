package net.datasa.project01.domain.entity; // 엔티티 클래스가 모여있는 패키지 선언

import jakarta.persistence.*; // JPA 관련 어노테이션 import
import lombok.*; // 롬복 어노테이션 import (코드 자동 생성)
import net.datasa.project01.domain.vo.VerificationPurpose; // 인증 목적 enum import

import java.time.LocalDateTime; // 날짜/시간 타입 import

/**
 * email_verifications 테이블과 매핑되는 엔티티 클래스
 *  - token_id: AUTO_INCREMENT PK (자동 증가 기본키)
 *  - token   : 고유 토큰(숫자 6자리 등), UNIQUE (중복 불가)
 *  - purpose : VERIFY_EMAIL/FIND_ID/RESET_PW (인증 목적)
 *  - expires_at: 만료시각
 *  - used_at   : 사용시각 (null이면 미사용)
 *  - created_at: DB default CURRENT_TIMESTAMP (읽기전용)
 */
@Getter // 모든 필드에 대한 getter 메서드 자동 생성
@Setter // 모든 필드에 대한 setter 메서드 자동 생성
@NoArgsConstructor // 파라미터 없는 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 받는 생성자 자동 생성
@Builder // 빌더 패턴 생성자 자동 생성
@Entity // JPA 엔티티임을 명시 (테이블과 매핑)
@Table(
        name = "email_verifications", // 매핑될 테이블명 지정
        indexes = {
                @Index(name = "idx_ev_email_purpose", columnList = "email, purpose, expires_at") // 복합 인덱스 생성
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ev_token", columnNames = "token") // token 컬럼에 유니크 제약조건
        }
)
public class EmailVerification {

    @Id // 기본키(PK)임을 명시
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB에서 자동 증가
    @Column(name = "token_id") // 컬럼명 지정
    private Long tokenId; // 인증 토큰의 고유 ID

    /** 인증 대상 이메일 주소 */
    @Column(name = "email", length = 100, nullable = false)
    private String email; // 인증 받을 이메일

    /** 토큰(고유) — 숫자 6자리 또는 랜덤 문자열 */
    @Column(name = "token", length = 100, nullable = false, unique = true) // 컬럼명, 길이, null 불가, 유니크
    private String token; // 인증에 사용되는 고유 토큰 값

    /** 용도 */
    @Enumerated(EnumType.STRING) // enum을 문자열로 저장
    @Column(name = "purpose", length = 20, nullable = false) // 컬럼명, 길이, null 불가
    private VerificationPurpose purpose; // 인증 목적 (이메일 인증, 아이디 찾기, 비밀번호 재설정 등)

    @Column(name = "expires_at", nullable = false) // 만료시각, null 불가
    private LocalDateTime expiresAt; // 토큰 만료 시각

    @Column(name = "used_at") // 사용시각, null 허용
    private LocalDateTime usedAt; // 토큰 사용 시각 (null이면 아직 사용 안함)

    /** DB가 기본값으로 채움 (읽기전용 매핑) */
    @Column(name = "created_at", insertable = false, updatable = false) // DB에서 자동 생성, 수정 불가
    private LocalDateTime createdAt; // 레코드 생성 시각 (DB에서 자동 입력)
}