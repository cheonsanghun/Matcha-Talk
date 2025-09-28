package net.datasa.project01.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowRequestDto;
import net.datasa.project01.domain.dto.FollowUpdateDto;
import net.datasa.project01.domain.dto.UserProfileUpdateRequest;
import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.ProfileRepository;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.service.email.EmailSender;
import net.datasa.project01.websocket.RealTimeMessagingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 회원/팔로우/프로필 관련 비즈니스 로직.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final ProfileRepository profileRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatService chatService;
    private final MatchService matchService;
    private final RealTimeMessagingService messagingService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeLoginId(String loginId) {
        String v = trim(loginId);
        return v == null ? null : v.toLowerCase();
    }

    private String normalizeEmail(String email) {
        String e = trim(email);
        return e == null ? null : e.toLowerCase();
    }

    /* ==============================
     * 회원가입
     * ============================== */
    @Transactional
    public UserResponse signUp(UserSignUpRequestDto req) {
        final String loginId = normalizeLoginId(req.getLoginId());
        final String email   = normalizeEmail(req.getEmail());

        if (userRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        emailVerificationService.verifyTokenForEmail(email, req.getVerificationToken());

        User user = User.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .nickName(trim(req.getNickName()))
                .email(email)
                .countryCode(trim(req.getCountryCode()))
                .languageCode(trim(req.getLanguageCode()) == null ? null : trim(req.getLanguageCode()).toLowerCase())
                .gender(req.getGender().charAt(0))
                .birthDate(req.getBirthDate())
                .emailVerified(true)
                .enabled(true)
                .roleName("ROLE_USER")
                .build();

        Profile profile = Profile.builder()
                .user(user)
                .visibility("PUBLIC")
                .build();
        user.setProfile(profile);

        try {
            User saved = userRepository.save(user);
            return UserResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("signUp unique constraint violated loginId={}, email={}", loginId, email, e);
            throw new IllegalArgumentException("이미 사용 중인 아이디 또는 이메일입니다.");
        }
    }

    public boolean existsByLoginId(String loginIdRaw) {
        return userRepository.existsByLoginId(normalizeLoginId(loginIdRaw));
    }

    public boolean existsByEmail(String emailRaw) {
        return userRepository.existsByEmail(normalizeEmail(emailRaw));
    }

    public UserResponse getUser(Long userPid) {
        User user = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponse.fromEntity(user);
    }

    public UserResponse getUserByLoginId(String loginId) {
        User user = userRepository.findByLoginId(normalizeLoginId(loginId))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponse.fromEntity(user);
    }

    /* ==============================
     * 아이디 찾기(이메일 발송)
     * ============================== */
    public void sendLoginIdToEmail(String emailRaw) {
        final String email = normalizeEmail(emailRaw);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 없습니다."));

        String subject = "[Matcha Talk] 아이디 찾기 안내";
        String body = String.format("안녕하세요.%n요청하신 로그인 아이디는 다음과 같습니다.%n%n▶ 로그인 아이디: %s%n%n보안상 타인에게 공유하지 말아주세요.%n감사합니다.",
                user.getLoginId());
        emailSender.send(email, subject, body);
    }

    /* ==============================
     * 프로필 기본 정보 수정
     * ============================== */
    @Transactional
    public UserResponse updateProfile(Long userPid, UserProfileUpdateRequest req) {
        User user = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (req.getNickName() != null) {
            user.setNickName(trim(req.getNickName()));
        }

        if (req.getEmail() != null) {
            final String newEmail = normalizeEmail(req.getEmail());
            final String oldEmail = user.getEmail();
            if (!Objects.equals(newEmail, oldEmail)) {
                userRepository.findByEmail(newEmail).ifPresent(other -> {
                    if (!Objects.equals(other.getUserPid(), user.getUserPid())) {
                        throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
                    }
                });
                user.setEmail(newEmail);
                user.setEmailVerified(false);
            }
        }

        if (req.getCountryCode() != null) {
            user.setCountryCode(trim(req.getCountryCode()));
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender().charAt(0));
        }
        if (req.getBirthDate() != null) {
            user.setBirthDate(req.getBirthDate());
        }

        Profile profile = profileRepository.findByUserUserPid(userPid)
                .orElseGet(() -> {
                    Profile p = Profile.builder()
                            .user(user)
                            .visibility("PUBLIC")
                            .build();
                    user.setProfile(p);
                    return p;
                });

        if (req.getAvatarUrl() != null) {
            profile.setAvatarUrl(req.getAvatarUrl().trim());
        }
        if (req.getBio() != null) {
            profile.setBio(req.getBio().trim());
        }
        if (req.getLanguages() != null) {
            try {
                profile.setLanguagesJson(objectMapper.writeValueAsString(req.getLanguages()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("지원하지 않는 언어 형식입니다.", e);
            }
        }
        if (req.getVisibility() != null) {
            profile.setVisibility(req.getVisibility().trim().toUpperCase());
        }
        profileRepository.save(profile);

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    /* ==============================
     * 팔로우 관리
     * ============================== */
    @Transactional
    public void createFollow(FollowRequestDto req, String followerLoginId) {
        User follower = userRepository.findByLoginId(normalizeLoginId(followerLoginId))
                .orElseThrow(() -> new IllegalArgumentException("요청한 사용자를 찾을 수 없습니다."));
        User followee = userRepository.findById(req.getFolloweeId())
                .orElseThrow(() -> new IllegalArgumentException("팔로우할 대상 사용자를 찾을 수 없습니다."));

        if (follower.getUserPid().equals(followee.getUserPid())) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
        }
        if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
            throw new IllegalStateException("이미 팔로우 요청을 보냈거나 팔로우 관계입니다.");
        }

        Follow follow = Follow.builder()
                .follower(follower)
                .followee(followee)
                .status(Follow.FollowStatus.PENDING)
                .build();
        followRepository.save(follow);
    }

    @Transactional
    public void updateFollowStatus(Long followId, FollowUpdateDto dto, String currentUsername) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팔로우 요청을 찾을 수 없습니다."));
        User currentUser = userRepository.findByLoginId(normalizeLoginId(currentUsername))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!follow.getFollowee().getUserPid().equals(currentUser.getUserPid())) {
            throw new IllegalStateException("이 요청을 처리할 권한이 없습니다.");
        }

        Follow.FollowStatus newStatus = Follow.FollowStatus.valueOf(dto.getStatus());
        if (newStatus != Follow.FollowStatus.ACCEPTED && newStatus != Follow.FollowStatus.REJECTED) {
            throw new IllegalArgumentException("잘못된 상태 값입니다: " + dto.getStatus());
        }
        follow.setStatus(newStatus);
        followRepository.save(follow);

        if (newStatus == Follow.FollowStatus.ACCEPTED) {
            handleMutualFollowPromotion(follow);
        }
    }

    @Transactional
    public void deleteFollow(Long followId, String currentUsername) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팔로우 관계를 찾을 수 없습니다."));
        User currentUser = userRepository.findByLoginId(normalizeLoginId(currentUsername))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!follow.getFollower().getUserPid().equals(currentUser.getUserPid())
                && !follow.getFollowee().getUserPid().equals(currentUser.getUserPid())) {
            throw new IllegalStateException("이 관계를 삭제할 권한이 없습니다.");
        }
        followRepository.delete(follow);
    }

    public List<UserResponse> getFollowingList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<Follow> relations = followRepository.findAllByFollowerAndStatus(user, Follow.FollowStatus.ACCEPTED);
        return relations.stream()
                .map(Follow::getFollowee)
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getFollowerList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<Follow> relations = followRepository.findAllByFolloweeAndStatus(user, Follow.FollowStatus.ACCEPTED);
        return relations.stream()
                .map(Follow::getFollower)
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private void handleMutualFollowPromotion(Follow follow) {
        followRepository.findByFollowerAndFolloweeAndStatus(
                        follow.getFollowee(),
                        follow.getFollower(),
                        Follow.FollowStatus.ACCEPTED)
                .ifPresent(ignored -> roomMemberRepository
                        .findFirstRandomRoomByUsers(
                                follow.getFollower().getUserPid(),
                                follow.getFollowee().getUserPid())
                        .ifPresent(randomRoom -> {
                            Room promotedRoom = chatService.promoteRandomRoom(randomRoom);
                            if (promotedRoom != null && promotedRoom.getRoomType() == Room.RoomType.PRIVATE) {
                                matchService.archiveMatchRequestsForRoom(promotedRoom);
                                messagingService.broadcastToUsers(
                                        java.util.List.of(
                                                follow.getFollower().getLoginId(),
                                                follow.getFollowee().getLoginId()
                                        ),
                                        RealTimeMessagingService.EVENT_MATCH_ROOM_PROMOTED,
                                        Map.of(
                                                "roomId", promotedRoom.getRoomId(),
                                                "temporary", false
                                        ));
                            }
                        }));
    }
}
