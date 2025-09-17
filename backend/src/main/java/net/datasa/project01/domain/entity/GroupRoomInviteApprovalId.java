package net.datasa.project01.domain.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@EqualsAndHashCode
public class GroupRoomInviteApprovalId implements Serializable {
    private Long invite;
    private Long approver;
}
