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
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", length = 10, nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private Integer capacity = 2; // TINYINT는 Integer로 매핑, 기본값 설정

    // 1:1 방이 그룹 방으로 전환되었을 때, 원본 방을 참조하기 위한 자기 자신과의 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_from_room_id")
    private Room createdFromRoom;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "promoted_reason", length = 30)
    private String promotedReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // --- ENUM 타입 정의 ---
    public enum RoomType {
        RANDOM, PRIVATE, GROUP
    }
}