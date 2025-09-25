package net.datasa.project01.domain.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RoomMember 엔티티의 복합 기본 키를 위한 ID 클래스
 */
@NoArgsConstructor
@EqualsAndHashCode // userPid와 roomId 값이 모두 같아야 동일 객체로 취급
public class RoomMemberId implements Serializable {

    private Long room; // RoomMember 엔티티의 'room' 필드명과 일치해야 함
    private Long user; // RoomMember 엔티티의 'user' 필드명과 일치해야 함
}