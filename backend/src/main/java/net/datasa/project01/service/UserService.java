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
}