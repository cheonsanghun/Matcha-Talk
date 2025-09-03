package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원(User) 엔티티 클래스
 * DB 테이블(users)과 매핑되며, 회원 인증/관리 관련 주요 정보를 담는다.
 */
@Getter @Setter
@Builder @AllArgsConstructor @NoArgsConstructor
@Entity // JPA 엔티티임을 명시
@Table(
        name = "users", // 매핑될 테이블명
        uniqueConstraints = { @UniqueConstraint(name = "uq_users_email", columnNames = "email") } // 이메일 유니크 제약
)
public class User {

    /**
     * 내부 PK (Primary Key)
     * DB: BIGINT, 직접 채번 (AUTO_INCREMENT 아님)
     */
    @Id
    @Column(name = "user_pid", nullable = false)
    private Long userPid;

    /**
     * 로그인 아이디
     * DB: VARCHAR(30), NOT NULL, UNIQUE
     * 회원 가입 시 중복 체크 필요
     */
    @Column(name = "login_id", length = 30, nullable = false, unique = true)
    private String loginId;

    /**
     * 해시된 비밀번호
     * DB: VARCHAR(255), NOT NULL
     * 실제 비밀번호는 저장하지 않음
     */
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /**
     * 닉네임(표시명)
     * DB: VARCHAR(30), NOT NULL
     */
    @Column(name = "nick_name", length = 30, nullable = false)
    private String nickName;

    /**
     * 이메일 주소
     * DB: VARCHAR(100), NOT NULL
     * 본인 인증 및 계정 복구용
     */
    @Column(name = "email", length = 100, nullable = false)
    private String email;

    /**
     * 국적 코드
     * DB: CHAR(2), NOT NULL
     * ISO-3166-1 alpha-2 (예: KR, US)
     */
    @Column(name = "country_code", length = 2, nullable = false, columnDefinition = "CHAR(2)")
    private String countryCode;

    /**
     * 성별
     * DB: CHAR(1), NOT NULL
     * 'M' 또는 'F' 값만 허용
     */
    @Column(name = "gender", length = 1, nullable = false, columnDefinition = "CHAR(1)")
    private String gender;

    /**
     * 생년월일
     * DB: DATE, NOT NULL
     * 가입 연령 하한 등은 앱/서버에서 별도 검증
     */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /**
     * 이메일 인증 여부
     * DB: TINYINT(1), NOT NULL
     * 0: 미인증, 1: 인증됨
     */
    @Column(name = "email_verified", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean emailVerified;

    /**
     * 연속 로그인 실패 횟수
     * DB: INT, NOT NULL
     * 5회 이상 실패 시 계정 잠금 처리
     */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /**
     * 계정 잠금 해제 예정 시각
     * DB: DATETIME, NULL 허용
     * 로그인 실패 누적 시 사용
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * 계정 사용 가능 여부
     * DB: TINYINT(1), NOT NULL
     * 1: 사용, 0: 정지
     */
    @Column(name = "enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean enabled;

    /**
     * 권한명
     * DB: VARCHAR(30), NOT NULL
     * 예: ROLE_USER, ROLE_ADMIN
     */
    @Column(name = "rolename", length = 30, nullable = false)
    private String roleName;

    /**
     * 생성 시각
     * DB: TIMESTAMP, 자동 생성
     * insert/update 불가 (DB에서 자동 관리)
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시각
     * DB: TIMESTAMP, 자동 갱신
     * insert/update 불가 (DB에서 자동 관리)
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}