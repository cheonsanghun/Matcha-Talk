package net.datasa.project01.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원(User) 엔티티 클래스
 * DB 테이블(users)과 매핑되며, 회원 인증/관리 관련 주요 정보를 담는다.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity // JPA 엔티티임을 명시
@Table(
        name = "users", // 매핑될 테이블명
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email", columnNames = "email") // 이메일 유니크 제약
        },
        indexes = {
                @Index(name = "idx_users_login", columnList = "login_id"),
                @Index(name = "idx_users_email", columnList = "email")
        }
)
public class User {

    /** 내부 PK (Primary Key) : BIGINT AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_pid", nullable = false)
    private Long userPid;

    /** 로그인 아이디 : VARCHAR(30) NOT NULL UNIQUE */
    @Column(name = "login_id", length = 30, nullable = false, unique = true)
    private String loginId;

    /** 해시된 비밀번호 : VARCHAR(255) NOT NULL (응답에 노출 금지) */
    @JsonIgnore
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /** 닉네임(표시명) : VARCHAR(30) NOT NULL */
    @Column(name = "nick_name", length = 30, nullable = false)
    private String nickName;

    /** 이메일 주소 : VARCHAR(100) NOT NULL */
    @Column(name = "email", length = 100, nullable = false)
    private String email;

    /** 국적 코드(ISO-3166-1 alpha-2) : CHAR(2) NOT NULL */
    @Column(name = "country_code", length = 2, nullable = false, columnDefinition = "CHAR(2)")
    private String countryCode;

    /** 성별(M/F) : CHAR(1) NOT NULL */
    @Column(name = "gender", length = 1, nullable = false, columnDefinition = "CHAR(1)")
    private String gender;

    /** 생년월일 : DATE NOT NULL */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** 이메일 인증 여부 : TINYINT(1) NOT NULL */
    @Column(name = "email_verified", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean emailVerified;

    /** 연속 로그인 실패 횟수 : INT NOT NULL */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /** 계정 잠금 해제 예정 시각(Null이면 잠금 아님) : DATETIME NULL */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 계정 사용 가능 여부(1:사용, 0:정지) : TINYINT(1) NOT NULL */
    @Column(name = "enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean enabled;

    /** 권한명(예: ROLE_USER, ROLE_ADMIN) : VARCHAR(30) NOT NULL */
    @Column(name = "rolename", length = 30, nullable = false)
    private String roleName;

    /** 생성 시각(DB 트리거/디폴트로 자동 관리) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시각(DB 트리거/디폴트로 자동 관리) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
