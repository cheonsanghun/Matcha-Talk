package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.datasa.project01.domain.dto.UserResponse;               // 회원 정보 응답 DTO
import net.datasa.project01.domain.dto.UserSignUpRequest;         // 회원 가입 요청 DTO
import net.datasa.project01.domain.dto.UserProfileUpdateRequest;  // 회원 프로필 수정 요청 DTO(본인용)
import net.datasa.project01.domain.entity.User;                   // 회원 엔티티
import net.datasa.project01.repository.UserRepository;            // 회원 저장소(인터페이스)
import net.datasa.project01.service.email.EmailSender;            // 이메일 발송 인터페이스
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 해시 인코더
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * [UserService]
 * - 회원가입, 회원 조회, 아이디 찾기, (본인) 프로필 수정 로직 담당
 *
 * 설계 메모
 * 1) 클래스 기본은 @Transactional(readOnly = true)로 두고, 쓰기 작업만 메서드 단위 @Transactional.
 * 2) DTO @Valid로 1차 검증 → 서비스에서는 중복/상태 등 비즈니스 규칙 검증.
 * 3) 이메일은 소문자, 국가코드/성별은 대문자로 정규화해서 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;

    // ==============================
    // 유틸
    // ==============================

    /** null-safe trim */
    private String trim(String s) { return s == null ? null : s.trim(); }

    /** 로그인 아이디: 트리밍(대소문자 구분 유지) */
    private String normalizeLoginId(String loginId) { return trim(loginId); }

    /** 이메일: 트리밍 + 소문자 */
    private String normalizeEmail(String email) {
        String e = trim(email);
        return e == null ? null : e.toLowerCase();
    }

    /** 엔티티 → 응답 DTO */
    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .userPid(u.getUserPid())
                .loginId(u.getLoginId())
                .nickName(u.getNickName())
                .email(u.getEmail())
                .countryCode(u.getCountryCode())
                .gender(u.getGender())
                .birthDate(u.getBirthDate())
                .roleName(u.getRoleName())
                // ✅ primitive boolean 이므로 isEnabled() 사용
                .enabled(u.isEnabled())
                .build();
    }

    // ==============================
    // 회원가입
    // ==============================

    /**
     * 회원가입 처리
     * - 아이디/이메일 중복 체크
     * - 이메일 인증 토큰 검증
     * - 비밀번호 해싱(BCrypt)
     * - 기본 정책 셋업
     */
    @Transactional
    public UserResponse signUp(UserSignUpRequest req) {
        // (0) 입력 정규화
        final String loginId = normalizeLoginId(req.getLoginId());
        final String email   = normalizeEmail(req.getEmail());

        // (1) 아이디/이메일 중복 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // (2) 이메일 인증 토큰 검증
        emailVerificationService.verifyTokenForEmail(email, req.getVerificationToken());

        // (3) 회원 엔티티 생성
        User user = User.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .nickName(trim(req.getNickName()))
                .email(email)
                .countryCode(trim(req.getCountryCode()))
                .gender(trim(req.getGender()))
                .birthDate(req.getBirthDate())
                .emailVerified(true)
                .failedLoginCount(0)
                .lockedUntil(null)
                .enabled(true)
                .roleName("ROLE_USER")
                .build();

        // (4) 저장 (DB 유니크 제약 위반 방어)
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("DataIntegrityViolation on signUp loginId={}, email={}", loginId, email, e);
            throw new IllegalArgumentException("이미 사용 중인 아이디 또는 이메일입니다.");
        }

        // (5) DTO 반환
        return toResponse(user);
    }

    /** 로그인 아이디 존재 여부 */
    public boolean existsByLoginId(String loginIdRaw) {
        return userRepository.existsByLoginId(normalizeLoginId(loginIdRaw));
    }

    /** 회원 단건 조회 */
    public UserResponse getUser(Long pid) {
        User u = userRepository.findById(pid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toResponse(u);
    }

    // ==============================
    // 아이디 찾기(이메일 발송)
    // ==============================

    /**
     * 입력 이메일의 가입자에게 로그인 아이디 발송
     */
    public void sendLoginIdToEmail(String emailRaw) {
        final String email = normalizeEmail(emailRaw);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 없습니다."));

        String subject = "[Matcha Talk] 아이디 찾기 안내";
        String body = String.format(
                "안녕하세요.%n" +
                        "요청하신 로그인 아이디는 다음과 같습니다.%n%n" +
                        "  ▶ 로그인 아이디: %s%n%n" +
                        "보안상 타인에게 공유하지 말아주세요.%n" +
                        "감사합니다.",
                user.getLoginId()
        );

        emailSender.send(email, subject, body);
    }

    // ==============================
    // (본인) 프로필 수정
    // ==============================

    /**
     * 회원 본인 프로필 수정 (허용 필드만 변경)
     * - 컨트롤러/시큐리티에서 본인 여부 확인 권장(JWT/세션의 userPid == path id)
     */
    @Transactional
    public UserResponse updateProfile(Long userPid, UserProfileUpdateRequest req) {
        User u = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 닉네임
        if (req.getNickName() != null) {
            u.setNickName(trim(req.getNickName()));
        }

        // 이메일 (정규화 + 중복검사 + 인증 리셋)
        if (req.getEmail() != null) {
            final String newEmail = normalizeEmail(req.getEmail());
            final String oldEmail = u.getEmail();

            if (!Objects.equals(newEmail, oldEmail)) {
                userRepository.findByEmail(newEmail).ifPresent(other -> {
                    if (!Objects.equals(other.getUserPid(), u.getUserPid())) {
                        throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
                    }
                });
                u.setEmail(newEmail);
                u.setEmailVerified(false); // 변경 시 재인증 요구
            }
        }

        // 국가코드/성별/생년월일
        if (req.getCountryCode() != null) u.setCountryCode(trim(req.getCountryCode()));
        if (req.getGender() != null)      u.setGender(trim(req.getGender()));
        if (req.getBirthDate() != null)   u.setBirthDate(req.getBirthDate());

        // 저장
        User saved = userRepository.save(u);
        return toResponse(saved);
    }

    // ==============================
    // (선택) 비밀번호 변경 – 필요시 사용
    // ==============================
    /*
    @Transactional
    public void changePassword(Long userPid, String currentRaw, String newRaw) {
        User u = userRepository.findById(userPid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentRaw, u.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        u.setPasswordHash(passwordEncoder.encode(newRaw));
        userRepository.save(u);
    }
    */
}
