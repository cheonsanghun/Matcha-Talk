package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "follow_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_fr_room_participants",
                        columnNames = {"room_id", "requester_pid", "receiver_pid"}
                )
        })
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_request_id")
    private Long followRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_pid", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_pid", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED
    }
}
