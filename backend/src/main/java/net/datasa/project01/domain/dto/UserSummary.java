package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [서버 -> 클라이언트] 응답에 실어 보낼 "안전한" 사용자 요약 정보
 * - 엔티티(User) 전체를 그대로 내보내면 안 됨(비번 해시 등 민감정보 노출 위험)
 * - 화면/상태 유지에 필요한 최소 필드만 담기
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserSummary {
    private Long   id;        // User.userPid
    private String loginId;   // User.loginId
    private String nickname;  // User.nickName
    private String email;     // User.email
}
