package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter // Profile과의 양방향 관계 설정을 위해 Setter 추가
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

    // [추가됨] language_code 필드 <- api번역 때문에 추가
    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(length = 1, nullable = false)
    private Character gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "failed_login_count")
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

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

    // 1:1 양방향 관계 설정
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Profile profile;
}