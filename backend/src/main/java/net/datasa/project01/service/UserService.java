package net.datasa.project01.service;

import jakarta.validation.Valid;
import net.datasa.project01.domain.dto.FollowRequestDto;
import net.datasa.project01.domain.dto.UserResponse;         // 회원 정보 응답 DTO
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.User;               // 회원 엔티티
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.UserRepository;        // 회원 저장소(인터페이스)
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 해시 인코더
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [UserService]
 * - 회원가입 및 회원 조회 비즈니스 로직을 담당하는 서비스 클래스입니다.
 * - DB/Mock 저장소에 관계없이 단일 인터페이스로 동작합니다.
 */
@Service // 스프링 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입 자동 생성
public class UserService {

    // 회원 저장소(인터페이스, mock/db 프로필에 따라 구현체 자동 주입)
    private final UserRepository userRepository;


    // 비밀번호 해시 인코더 (BCrypt 등)
    private final PasswordEncoder passwordEncoder;

    // 이메일 인증 서비스
    private final EmailVerificationService emailVerificationService;

    private final FollowRepository followRepository;

    /**
     * 회원가입 처리 메서드
     * 1. 아이디/이메일 중복 체크
     * 2. 이메일 인증 토큰 검증
     * 3. 비밀번호 해시 처리 및 회원 엔티티 생성
     * 4. 회원 정보 저장
     * 5. 응답 DTO로 변환
     *
     * @param req 회원가입 요청 DTO
     * @return UserResponse 회원 정보 응답 DTO
     */
    @Transactional // DB 저장 시 트랜잭션 처리(mock에선 영향 없음)
    public UserResponse signUp(@Valid UserSignUpRequestDto req) {

        // (1) 아이디 중복 체크
        if (userRepository.existsByLoginId(req.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        // (1) 이메일 중복 체크
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // (2) 이메일 인증 토큰 검증
        emailVerificationService.verifyTokenForEmail(req.getEmail(), req.getVerificationCode());

        // (3) 회원 엔티티 생성 (비밀번호 해시 포함)
        String genderValue = req.getGender() != null ? req.getGender().trim().toUpperCase() : null;
        if (genderValue == null || genderValue.isEmpty()) {
            throw new IllegalArgumentException("성별 정보가 올바르지 않습니다.");
        }
        Character genderChar = genderValue.charAt(0);
        User user = User.builder()
                .loginId(req.getLoginId())                      // 로그인 아이디
                .passwordHash(passwordEncoder.encode(req.getPassword())) // 비밀번호 해시
                .nickName(req.getNickName())                    // 닉네임
                .email(req.getEmail())                          // 이메일
                .countryCode(req.getCountryCode())              // 국적 코드
                .gender(genderChar)                        // 성별
                .birthDate(req.getBirthDate())                  // 생년월일
                .emailVerified(true)                            // 이메일 인증 여부
                .failedLoginCount(0)                            // 로그인 실패 횟수(초기값 0)
                .lockedUntil(null)                              // 계정 잠금 해제 시각(초기값 null)
                .enabled(true)                                  // 계정 사용 가능 여부(초기값 true)
                .roleName("ROLE_USER")                          // 기본 권한명
                .build();

        // (4) 회원 정보 저장 (mock: 메모리, db: JPA)
        userRepository.save(user);

        // (5) 응답 DTO로 변환 (민감 정보 제외)
        return UserResponse.builder()
                .userPid(user.getUserPid())                     // 회원 PK
                .loginId(user.getLoginId())                     // 로그인 아이디
                .nickName(user.getNickName())                   // 닉네임
                .email(user.getEmail())                         // 이메일
                .countryCode(user.getCountryCode())             // 국적 코드
                .gender(user.getGender())                       // 성별
                .birthDate(user.getBirthDate())                 // 생년월일
                .roleName(user.getRoleName())                   // 권한명
                .enabled(user.isEnabled())                      // 계정 사용 가능 여부
                .build();
    }

    /**
     * 로그인 아이디 존재 여부 반환
     */
    public boolean existsByLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    /** 이메일 존재 여부 반환 */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /** 로그인 아이디로 회원 조회 */
    public UserResponse getUserByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponse.fromEntity(user);
    }

    /**
     * 회원 단건 조회 메서드
     * - 회원 PK로 회원 정보를 조회하여 응답 DTO로 반환합니다.
     *
     * @param pid 회원 PK
     * @return UserResponse 회원 정보 응답 DTO
     */
    public UserResponse getUser(Long pid) {
        // 회원 정보 조회 (없으면 예외 발생)
        User u = userRepository.findById(pid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        // 응답 DTO로 변환
        return UserResponse.builder()
                .userPid(u.getUserPid())
                .loginId(u.getLoginId())
                .nickName(u.getNickName())
                .email(u.getEmail())
                .countryCode(u.getCountryCode())
                .gender(u.getGender())
                .birthDate(u.getBirthDate())
                .roleName(u.getRoleName())
                .enabled(u.isEnabled())
                .build();
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