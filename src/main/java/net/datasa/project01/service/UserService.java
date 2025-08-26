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
            // [수정됨] String의 첫 번째 글자를 Character로 변환하여 저장
            .gender(requestDto.getGender().charAt(0)) 
            .birthDate(LocalDate.parse(requestDto.getBirthDate()))
            .enabled(true)
            .rolename("ROLE_USER")
            .build();
    
    // ... (Profile 생성 및 저장 로직은 동일)
    Profile profile = Profile.builder()
            .user(user)
            .visibility("PUBLIC")
            .build();
            
    user.setProfile(profile);

    userRepository.save(user);
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
        
        // --- 디버깅 로그 추가 ---
        logger.info("Attempting to find user profile for loginId from Security Context: '{}'", loginId);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> {
                    // --- 에러 발생 시에도 로그 추가 ---
                    logger.error("User not found in DB for loginId: '{}'", loginId);
                    return new IllegalArgumentException("해당 사용자를 찾을 수 없습니다.");
                });
        
        logger.info("Successfully found user: {}", user.getNickName());
        return new UserProfileResponseDto(user);
    }
}
