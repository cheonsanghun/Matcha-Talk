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

// UserService.java의 signUp 메소드 내부

    @Transactional
    public void signUp(UserSignUpRequestDto requestDto) {
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
    
    User user = userRepository.findByLoginId(requestDto.getLoginId())
            .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));

    if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
        throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

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
}
