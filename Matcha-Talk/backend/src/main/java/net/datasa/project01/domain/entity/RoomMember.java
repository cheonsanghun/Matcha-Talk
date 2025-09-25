package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "room_members")
@IdClass(RoomMemberId.class) // RoomMemberId 클래스를 이 엔티티의 ID 클래스로 지정
public class RoomMember {

    @Id // 복합 키의 일부임을 선언
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Id // 복합 키의 일부임을 선언
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_pid")
    private User user;

    @Column(length = 10, nullable = false)
    @Builder.Default
    private String role = "MEMBER";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_pid")
    private User invitedByUser;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;
}
