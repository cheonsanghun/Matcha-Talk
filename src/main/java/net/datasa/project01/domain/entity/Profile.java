package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_pid")
    private Long userPid; // users 테이블의 PK를 그대로 PK이자 FK로 사용

    // @MapsId를 사용하여 userPid 필드가 User 엔티티의 ID와 매핑됨을 명시
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_pid")
    private User user;

    @Column(name = "avatar_url", length = 300)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    @Column(name = "languages_json", columnDefinition = "JSON")
    private String languagesJson;

    @Column(length = 10, nullable = false)
    private String visibility = "PUBLIC";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}