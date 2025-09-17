package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * [SecurityConfig]
 * - 테스트 및 개발 편의성을 위해 모든 HTTP 요청을 인증 없이 허용합니다.
 * - REST API 환경에 맞춰 CSRF, 폼 로그인 등을 비활성화합니다.
 * - 비밀번호 저장 시 BCrypt 해시를 사용하도록 PasswordEncoder 빈을 제공합니다.
 */
@Configuration // 스프링 설정 클래스임을 명시
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * SecurityFilterChain 빈 등록
     * - 모든 요청을 permitAll()로 허용
     * - actuator, api/users 경로도 별도 허용
     * - CSRF 보호 비활성화 (REST/테스트 환경)
     * - HTTP Basic 인증 활성화(테스트용, 실제 서비스에서는 비활성화 권장)
     * - 폼 로그인 비활성화 (REST API 환경)
     */
    @Bean
    SecurityFilterChain http(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API나 테스트 환경에서는 불필요)
                .csrf(csrf -> csrf.disable())
                // HTTP 요청 권한 설정
                .authorizeHttpRequests(reg -> reg
                        // actuator, api/users 경로는 모두 허용
                        .requestMatchers("/actuator/**", "/api/users/**").permitAll()
                        // 그 외 모든 요청도 허용
                        .anyRequest().permitAll()
                )
                // HTTP Basic 인증 활성화 (테스트용, 실제 서비스에서는 비활성화 권장)
                .httpBasic(Customizer.withDefaults())
                // 폼 로그인 비활성화 (REST API 환경에서는 사용하지 않음)
                .formLogin(form -> form.disable());
        // 최종 SecurityFilterChain 반환
        return http.build();
    }

    /**
     * PasswordEncoder 빈 등록
     * - 비밀번호 해시 저장 시 BCrypt 알고리즘 사용
     * - 회원 가입/로그인 시 비밀번호 암호화 및 검증에 사용
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCrypt 해시 인코더 반환
    }
}