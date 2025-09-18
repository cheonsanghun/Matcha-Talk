package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
     */
    @Bean
    SecurityFilterChain http(HttpSecurity http) throws Exception {
        // JWT 인증 필터 생성
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtUtil, userDetailsService);

        http
                // CSRF 보호 비활성화 (REST API나 테스트 환경에서는 불필요)
                .csrf(csrf -> csrf.disable())
                // 세션을 사용하지 않도록 설정 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HTTP 요청 권한 설정
                .authorizeHttpRequests(reg -> reg
                        // WebSocket 연결 경로 허용
                        .requestMatchers("/ws-stomp/**").permitAll()
                        // 회원가입, 로그인, 중복확인, 이메일 인증 등 인증 없이 접근해야 하는 경로 허용
                        .requestMatchers("/api/auth/login", "/api/users/signup", "/api/users/exists", "/api/users/email/**").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // 폼 로그인 비활성화 (REST API 환경에서는 사용하지 않음)
                .formLogin(form -> form.disable())
                // HTTP Basic 인증 비활성화
                .httpBasic(httpBasic -> httpBasic.disable())
                // UsernamePasswordAuthenticationFilter 앞에 JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
