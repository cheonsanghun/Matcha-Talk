package net.datasa.project01.service;

import net.datasa.project01.domain.dto.FollowRequestDto;
import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 회원가입 및 회원 조회 비즈니스 로직을 담당하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    /**
     * 회원가입 처리 메서드
     */
    @Transactional
    public UserResponse signUp(UserSignUpRequestDto req) {

        // 아이디 및 이메일 중복 체크
        if (userRepository.existsByLoginId(req.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 회원가입 전, 이메일 인증이 완료되었는지 최종 확인
        emailVerificationService.verifyTokenForEmail(req.getEmail(), req.getVerificationCode());

        // User 엔티티 생성
        User user = User.builder()
                .loginId(req.getLoginId())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .nickName(req.getNickName())
                .email(req.getEmail())
                .countryCode(req.getCountryCode())
                .languageCode(req.getLanguageCode())    // dto에서 입력받은 언어값으로 언어설정 나중에 프론트에 (사용 언어)추가로 설정하면 될 듯
                .gender(req.getGender().charAt(0)) // String -> Character 변환
                .birthDate(req.getBirthDate())
                .emailVerified(true) // 이 단계까지 왔다면 이메일 인증이 성공한 것임
                .enabled(true)
                .rolename("ROLE_USER")
                .build();
        
        // Profile 엔티티 생성 및 연결
        Profile profile = Profile.builder()
                .user(user)
                .visibility("PUBLIC")
                .build();
        user.setProfile(profile);

        // 회원 정보 저장
        User savedUser = userRepository.save(user);

        // 응답 DTO로 변환하여 반환
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * 로그인 아이디 존재 여부 반환
     */
    @Transactional(readOnly = true)
    public boolean existsByLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }
    
    /**
     * 이메일 존재 여부 반환
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 회원 단건 조회 메서드
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long pid) {
        User user = userRepository.findById(pid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 응답 DTO로 변환하여 반환
        return UserResponse.fromEntity(user);
    }
    /**
     * 로그인 ID로 회원을 조회하여 응답 DTO로 반환
     * @param loginId 조회할 사용자의 로그인 ID
     * @return UserResponse 회원 정보 응답 DTO
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // User 엔티티를 UserResponse DTO로 변환하여 반환
        return UserResponse.fromEntity(user);
    }

    /**
     * 팔로우 요청 생성
     */
    @Transactional
    public void createFollow(FollowRequestDto req, String followerLoginId) {
        User follower = userRepository.findByLoginId(followerLoginId)
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

    /**
     * 팔로우 요청 상태 변경 (수락/거절)
     */
    @Transactional
    public void updateFollowStatus(Long followId, net.datasa.project01.domain.dto.FollowUpdateDto dto, String currentUsername) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팔로우 요청을 찾을 수 없습니다."));

        User currentUser = userRepository.findByLoginId(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 요청을 받은 사람(followee)만 상태를 변경할 수 있음
        if (!follow.getFollowee().getUserPid().equals(currentUser.getUserPid())) {
            throw new IllegalStateException("이 요청을 처리할 권한이 없습니다.");
        }

        Follow.FollowStatus newStatus = Follow.FollowStatus.valueOf(dto.getStatus());
        if (newStatus != Follow.FollowStatus.ACCEPTED && newStatus != Follow.FollowStatus.REJECTED) {
            throw new IllegalArgumentException("잘못된 상태 값입니다: " + dto.getStatus());
        }

        follow.setStatus(newStatus);
        followRepository.save(follow);
    }

    /**
     * 팔로우 관계 삭제 (언팔로우)
     */
    @Transactional
    public void deleteFollow(Long followId, String currentUsername) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팔로우 관계를 찾을 수 없습니다."));

        User currentUser = userRepository.findByLoginId(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 팔로우를 요청한 사람(follower) 또는 받은 사람(followee)만 삭제할 수 있음
        if (!follow.getFollower().getUserPid().equals(currentUser.getUserPid()) && !follow.getFollowee().getUserPid().equals(currentUser.getUserPid())) {
            throw new IllegalStateException("이 관계를 삭제할 권한이 없습니다.");
        }

        followRepository.delete(follow);
    }

    /**
     * 사용자가 팔로우하는 사람들의 목록을 조회
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getFollowingList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<Follow> followingRelations = followRepository.findAllByFollowerAndStatus(user, Follow.FollowStatus.ACCEPTED);

        return followingRelations.stream()
                .map(Follow::getFollowee)
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 사용자를 팔로우하는 사람들의 목록을 조회
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getFollowerList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<Follow> followerRelations = followRepository.findAllByFolloweeAndStatus(user, Follow.FollowStatus.ACCEPTED);

        return followerRelations.stream()
                .map(Follow::getFollower)
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }
}