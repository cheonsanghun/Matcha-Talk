package net.datasa.project01.service;

import net.datasa.project01.domain.dto.UserResponse;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 및 회원 조회 비즈니스 로직을 담당하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // private final EmailVerificationService emailVerificationService; // TODO: EmailVerificationService 구현 후 주석 해제

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

        // TODO: EmailVerificationService 구현 후 아래 주석 해제
        // emailVerificationService.verifyTokenForEmail(req.getEmail(), req.getVerificationToken());

        // User 엔티티 생성
        User user = User.builder()
                .loginId(req.getLoginId())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .nickName(req.getNickName())
                .email(req.getEmail())
                .countryCode(req.getCountryCode())
                .gender(req.getGender().charAt(0)) // String -> Character 변환
                .birthDate(req.getBirthDate())
                .emailVerified(true) // TODO: 이메일 인증 기능 구현 후 false로 변경하고, 인증 완료 시 true로 업데이트
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
}