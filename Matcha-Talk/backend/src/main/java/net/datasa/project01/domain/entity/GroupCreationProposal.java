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
@Table(name = "group_creation_proposals")
public class GroupCreationProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_id")
    private Long proposalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "private_room_id", nullable = false)
    private Room privateRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee1_pid", nullable = false)
    private User invitee1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee2_pid", nullable = false)
    private User invitee2;

    @Column(name = "user1_approve")
    @Builder.Default
    private boolean user1Approve = false;

    @Column(name = "user2_approve")
    @Builder.Default
    private boolean user2Approve = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ProposalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }
}
