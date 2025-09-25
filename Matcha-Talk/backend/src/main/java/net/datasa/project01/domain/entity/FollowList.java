package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "follow_list",
       uniqueConstraints = {
               @UniqueConstraint(
                       name = "uq_fl_owner_target_dir",
                       columnNames = {"owner_pid", "target_pid", "direction"}
               )
       })
public class FollowList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "list_id")
    private Long listId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_id", nullable = false)
    private Follow follow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_pid", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_pid", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private FollowDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Follow.FollowStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum FollowDirection {
        FOLLOWING,
        FOLLOWER
    }
}
