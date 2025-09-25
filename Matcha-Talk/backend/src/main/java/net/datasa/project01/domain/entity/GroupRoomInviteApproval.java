package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "group_room_invite_approvals")
@IdClass(GroupRoomInviteApprovalId.class)
public class GroupRoomInviteApproval {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_id")
    private GroupRoomInvite invite;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_pid")
    private User approver;

    @Column(nullable = false)
    @Builder.Default
    private boolean approved = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
