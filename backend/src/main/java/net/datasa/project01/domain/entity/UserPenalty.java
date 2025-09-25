package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * UserPenalty 엔티티는 사용자의 제재(경고, 정지, 영구정지 등) 이력을 저장하는 테이블과 매핑됩니다.
 * - 각 제재는 특정 사용자(User)와 연결됩니다.
 * - 제재 유형, 사유, 시작/종료 시각, 생성 시각을 포함합니다.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "user_penalties") // 실제 DB 테이블명 지정
public class UserPenalty {

    /**
     * 제재 고유 ID (PK, auto increment)
     * - DB 컬럼: penalty_id
     * - 자동 증가 전략(IDENTITY) 사용
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id")
    private Long penaltyId;

    /**
     * 제재 대상 사용자 (User 엔티티와 다대일 관계)
     * - user_pid 컬럼(FK)로 연결
     * - nullable=false: 반드시 사용자 정보가 있어야 함
     * - fetch=EAGER: 조회 시 항상 User 정보도 함께 로딩 (API 응답 직렬화 편의)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_pid", nullable = false)
    private User user;

    /**
     * 제재 유형
     * - 예: WARN(경고), SUSPEND(일시정지), BAN(영구정지)
     * - 최대 20자, null 불가
     */
    @Column(nullable = false, length = 20)
    private String type;

    /**
     * 제재 사유(관리자 입력)
     * - 최대 200자, null 허용(사유가 없을 수도 있음)
     */
    @Column(length = 200)
    private String reason;

    /**
     * 제재 시작 시각
     * - null 불가(모든 제재는 시작 시각이 반드시 존재)
     * - DB 컬럼명: starts_at
     */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /**
     * 제재 종료 시각
     * - BAN(영구정지)인 경우 null(무기한)
     * - 그 외에는 정지 해제 시각 등으로 사용
     * - DB 컬럼명: ends_at
     */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    /**
     * 제재 레코드 생성 시각
     * - DB에서 자동 생성 (insertable=false, updatable=false)
     * - 엔티티 생성 시점에 직접 할당하지 않음
     * - DB 트리거/DEFAULT CURRENT_TIMESTAMP 등으로 관리
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}