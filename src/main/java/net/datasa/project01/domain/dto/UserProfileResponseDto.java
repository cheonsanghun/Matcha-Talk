package net.datasa.project01.domain.dto;

import lombok.Getter;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.User;

@Getter 
public class UserProfileResponseDto {
    
    // User 엔티티에서 가져올 정보
    private final String loginId;
    private final String nickName;
    private final String email;
    private final String countryCode;

    // Profile 엔티티에서 가져올 정보
    private final String avatarUrl;
    private final String bio;
    private final String visibility;
    private final String languagesJson;

    /**
     * User 엔티티 (+연관된 Profile 엔티티)를 DTO로 변환하기 위한 생성자
     * @param user 데이터베이스에서 조회한 User 엔티티 객체
     */
    public UserProfileResponseDto(User user) {
        // User 엔티티의 필드 매핑
        this.loginId = user.getLoginId();
        this.nickName = user.getNickName();
        this.email = user.getEmail();
        this.countryCode = user.getCountryCode();

        // User 엔티티에 연결된 Profile 엔티티를 가져오기
        Profile profile = user.getProfile();
        if (profile != null) {
            // Profile 엔티티의 필드 매핑
            this.avatarUrl = profile.getAvatarUrl();
            this.bio = profile.getBio();
            this.visibility = profile.getVisibility();
            this.languagesJson = profile.getLanguagesJson();
        } else {
            // 프로필이 없는 경우 기본값 처리
            this.avatarUrl = null;
            this.bio = null;
            this.visibility = "PUBLIC";
            this.languagesJson = null;
        }
    }
}
