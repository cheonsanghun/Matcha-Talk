package net.datasa.project01.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "user_inquiries",
        indexes = {@Index(name = "idx_ui_user_status", columnList = "user_pid,status,created_at")})
public class UserInquiry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pid")
    @JsonIgnoreProperties({"passwordHash","failedLoginCount","lockedUntil"})
    private User user;

    @Column(length = 30, nullable = false)
    private String category;

    @Column(length = 200, nullable = false)
    private String title;

    @Lob @Column(nullable = false)
    private String content;

    @Column(length = 10, nullable = false)
    private String status; // OPEN / ANSWERED / CLOSED

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
