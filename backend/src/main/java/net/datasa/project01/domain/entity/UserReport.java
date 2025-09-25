// backend/src/main/java/net/datasa/project01/domain/entity/UserReport.java
package net.datasa.project01.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
// Hibernate 프록시 직렬화 이슈 회피용
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Entity
@Table(name = "user_reports",
        indexes = {
                @Index(name = "idx_ur_status_time", columnList = "status,created_at"),
                @Index(name = "idx_ur_participants", columnList = "reporter_pid,reported_pid")
        })
public class UserReport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_pid")
    // 민감필드 가리는 차원에서 기본 필드 몇 개만 무시
    @JsonIgnoreProperties({"passwordHash","failedLoginCount","lockedUntil"})
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_pid")
    @JsonIgnoreProperties({"passwordHash","failedLoginCount","lockedUntil"})
    private User reported; // DDL: reported_pid

    @Column(length = 100, nullable = false)
    private String reason;

    @Lob
    private String detail;

    @Column(length = 12, nullable = false)
    private String status; // OPEN / REVIEWING / ACTIONED / DISMISSED

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
