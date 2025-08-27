package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * users 테이블과 1:1 매핑되는 JPA 엔티티
 *  - 컬럼명은 SQL에 맞춰 @Column(name="...")으로 정확히 지정
 *  - created_at / updated_at은 DB가 채우므로 읽기 전용 매핑
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "users") // 정확히 SQL의 테이블명과 동일
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
    @Column(name = "user_pid")
    private Long userPid; // PK (개인 식별 번호)

    @Column(name = "login_id", nullable = false, length = 30, unique = true)
    private String loginId; // 로그인 ID (대소문자 구분, utf8mb4_bin)

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash; // 암호화 비밀번호(BCrypt 등)

    @Column(name = "nick_name", nullable = false, length = 30)
    private String nickName; // 표시 이름

    @Column(name = "email", length = 100)
    private String email; // (선택)

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode; // 국가 코드 (예: KR, JP)

    @Column(name = "gender", nullable = false, length = 1)
    private String gender; // 'M' or 'F'

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate; // 생년월일

    @Column(name = "enabled", nullable = false)
    private Boolean enabled; // 1:사용, 0:정지 -> 자바에선 true/false
    // 주의) DB 기본값이 1이지만, JPA가 null로 넣을 수 있으니 코드에서 기본 true로 세팅해주는 게 안전

    @Column(name = "rolename", nullable = false, length = 30)
    private String roleName; // 'ROLE_USER' 또는 'ROLE_ADMIN'

    // DB가 자동으로 채우는 타임스탬프 (읽기 전용으로 매핑)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}