package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.datasa.project01.domain.entity.Room;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 매칭 요청 정보를 저장하는 엔티티
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "match_requests")
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    // [수정됨] Long userPid -> User user
    // User 엔티티와 다대일(N:1) 관계를 맺습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_pid", nullable = false)
    private User user;

    // [수정됨] String -> Enum
    @Enumerated(EnumType.STRING)
    @Column(name = "choice_gender", length = 1, nullable = false)
    private Gender choiceGender;

    @Column(name = "min_age", nullable = false)
    private Integer minAge; // int -> Integer 로 변경 (null 처리 등 유연성)

    @Column(name = "max_age", nullable = false)
    private Integer maxAge; // int -> Integer 로 변경

    @Column(name = "region_code", length = 10, nullable = false)
    private String regionCode;

    @Column(name = "interests_json", columnDefinition = "JSON", nullable = false)
    private String interestsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    // [수정됨] String -> Enum
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private MatchStatus status = MatchStatus.WAITING;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    // --- ENUM 타입 정의 ---
    public enum Gender {
        M, F, A // Male, Female, Any
    }

    public enum MatchStatus {
        WAITING,
        MATCHED,
        CONFIRMED,
        DECLINED,
        CANCELLED
    }
}
