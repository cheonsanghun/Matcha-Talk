package net.datasa.project01.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.UserLoginRequestDto;
import net.datasa.project01.domain.dto.UserProfileResponseDto;
import net.datasa.project01.domain.dto.UserSignUpRequestDto;
import net.datasa.project01.domain.entity.Profile;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.util.JwtUtil;

import java.time.LocalDate;
import org.slf4j.Logger;      
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    // TODO: 이메일 서비스 추가 (회원가입 인증, 비밀번호 재설정)
    // private final EmailService emailService;
    // TODO: Redis 캐시 서비스 추가 (사용자 세션 관리)
    // private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void signUp(UserSignUpRequestDto requestDto) {
        // TODO: 이메일 인증 단계 추가
        // TODO: 중복 닉네임 검증 강화
        // TODO: 비밀번호 복잡도 검증
        if (userRepository.findByLoginId(requestDto.getLoginId()).isPresent() ||
            userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 아이디 또는 이메일입니다.");
        }

        User user = User.builder()
            .loginId(requestDto.getLoginId())
            .passwordHash(passwordEncoder.encode(requestDto.getPassword()))
            .nickName(requestDto.getNickName())
            .email(requestDto.getEmail())
            .countryCode(requestDto.getCountryCode())
            .gender(requestDto.getGender().charAt(0)) 
            .birthDate(LocalDate.parse(requestDto.getBirthDate()))
            .enabled(true)
            .rolename("ROLE_USER")
            .build();

        Profile profile = Profile.builder()
            .user(user) // 관계의 주인인 User 엔티티 설정
            .visibility("PUBLIC") // 기본값 설정
            .build();

        user.setProfile(profile); // 양방향 관계 설정

        userRepository.save(user); // User만 저장해도 Profile이 함께 저장됨 (cascade)
    }

    public String login(UserLoginRequestDto requestDto) {
        // TODO: 로그인 시도 횟수 제한 기능 추가
        // TODO: 다중 디바이스 로그인 관리
        // TODO: 로그인 이력 저장
        
        User user = userRepository.findByLoginId(requestDto.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            // TODO: 실패 횟수 증가 로직 추가
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        
        // TODO: 로그인 성공 시 세션 정보 Redis에 저장
        // TODO: Refresh Token 발급 로직 추가
        return jwtUtil.createToken(user.getLoginId());
    }
    
    @Transactional(readOnly = true)
    public UserProfileResponseDto getMyProfile() {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        logger.info("Attempting to find user profile for loginId: '{}'", loginId);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> {
                    logger.error("User not found in DB for loginId: '{}'", loginId);
                    return new IllegalArgumentException("해당 사용자를 찾을 수 없습니다.");
                });
        
        logger.info("Successfully found user: {}", user.getNickName());
        
        // User 엔티티를 DTO로 변환하여 반환
        // DTO의 생성자에서 Profile 정보까지 함께 처리
        return new UserProfileResponseDto(user);
    }
    
    // TODO: 추가 필요한 메서드들
    // public void updateProfile(UserUpdateRequestDto dto) { }
    // public void changePassword(ChangePasswordRequestDto dto) { }
    // public void resetPassword(String email) { }
    // public void deactivateAccount(String loginId) { }
    // public List<UserSearchResponseDto> searchUsers(String keyword) { }
    // public void updateLastActiveTime(String loginId) { }
    // public boolean isUserOnline(String loginId) { }
}
