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
    private Long userPid;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // userPid 필드를 User 엔티티의 ID와 매핑
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