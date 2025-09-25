package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.User;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class UserResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LANG_TYPE = new TypeReference<>() {};

    private final Long userPid;
    private final String loginId;
    private final String nickName;
    private final String email;
    private final String countryCode;
    private final String languageCode;
    private final Character gender;
    private final LocalDate birthDate;
    private final String roleName;
    private final boolean emailVerified;
    private final boolean enabled;
    private final String avatarUrl;
    private final String bio;
    private final List<String> languages;
    private final String visibility;

    public static UserResponse fromEntity(User user) {
        Profile profile = user.getProfile();
        List<String> languages = Collections.emptyList();
        if (profile != null && profile.getLanguagesJson() != null) {
            try {
                languages = MAPPER.readValue(profile.getLanguagesJson(), LANG_TYPE);
            } catch (Exception ignored) {
                languages = Collections.emptyList();
            }
        }

        return UserResponse.builder()
                .userPid(user.getUserPid())
                .loginId(user.getLoginId())
                .nickName(user.getNickName())
                .email(user.getEmail())
                .countryCode(user.getCountryCode())
                .languageCode(user.getLanguageCode())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .roleName(user.getRoleName())
                .emailVerified(user.isEmailVerified())
                .enabled(user.isEnabled())
                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .bio(profile != null ? profile.getBio() : null)
                .languages(languages)
                .visibility(profile != null ? profile.getVisibility() : null)
                .build();
    }
}
