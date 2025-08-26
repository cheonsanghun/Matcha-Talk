package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_pid")
    private Long userPid;

    @Column(name = "login_id", length = 30, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "nick_name", length = 30, nullable = false)
    private String nickName;

    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(length = 1, nullable = false)
    private Character gender; // CHAR(1)은 Character 타입과 매핑

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate; // DATE는 LocalDate와 매핑

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil; // DATETIME은 LocalDateTime과 매핑

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 30)
    private String rolename = "ROLE_USER";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 1:1 관계 설정. Profile 엔티티의 'user' 필드에 의해 매핑됨을 명시
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Profile profile;
}
