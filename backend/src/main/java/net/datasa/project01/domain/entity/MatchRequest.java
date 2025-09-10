package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
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


    @Column(name = "user_pid", nullable = false)
    private Long userPid;

    @Column(name = "choice_gender", length = 1, nullable = false)
    private String choiceGender;

    @Column(name = "min_age", nullable = false)
    private int minAge;

    @Column(name = "max_age", nullable = false)
    private int maxAge;

    @Column(name = "region_code", length = 10, nullable = false)
    private String regionCode;

    @Column(name = "interests_json", columnDefinition = "JSON", nullable = false)
    private String interestsJson;

    @Builder.Default
    @Column(name = "status", length = 10, nullable = false)
    private String status = "WAITING";


    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;
}
