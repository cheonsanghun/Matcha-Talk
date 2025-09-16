package net.datasa.project01.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 애플리케이션 전반의 HTTP 보안 정책을 구성합니다.
 * <ul>
 *     <li>회원가입/로그인 등 공개 API는 permitAll()로 허용합니다.</li>
 *     <li>매칭 관련 API(`/api/match/**`)는 JWT 인증이 필요합니다.</li>
 *     <li>REST API 환경에 맞춰 CSRF와 폼 로그인을 비활성화합니다.</li>
 *     <li>비밀번호 저장 시 BCrypt 해시를 사용하도록 PasswordEncoder 빈을 제공합니다.</li>
 * </ul>
 */
@Configuration // 스프링 설정 클래스임을 명시
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/**",
            "/api/users/**",
            "/api/auth/**"
    };

    private static final String[] PROTECTED_ENDPOINTS = {
            "/api/match/**"
    };

    /**
     * SecurityFilterChain 빈 등록
     * - REST API 환경에 맞춰 CSRF, 폼 로그인을 비활성화합니다.
     * - 공개 엔드포인트는 permitAll(), 매칭 관련 엔드포인트는 authenticated()로 제어합니다.
     * - JWT 전용 인증 필터와 인증 실패 처리기를 필터 체인에 연결합니다.
     */
    @Bean
    SecurityFilterChain http(
            HttpSecurity http,
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService) throws Exception {
        List<RequestMatcher> protectedMatchers = protectedEndpointMatchers();

        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(
                        jwtUtil,
                        userDetailsService,
                        protectedMatchers);

        http
                // CSRF 보호 비활성화 (REST API나 테스트 환경에서는 불필요)
                .csrf(csrf -> csrf.disable())
                // HTTP 요청 권한 설정
                .authorizeHttpRequests(reg -> reg
                        // 인증 없이 접근 가능한 엔드포인트(회원가입/로그인 등)
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // 매칭 관련 엔드포인트는 JWT 인증 필요
                        .requestMatchers(PROTECTED_ENDPOINTS).authenticated()
                        // 그 외 모든 요청도 허용 (필요 시 확장)
                        .anyRequest().permitAll()
                )
                // JWT 인증 필터 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 인증 실패 시 처리 핸들러 등록
                .exceptionHandling(e -> e.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
                // 폼 로그인 비활성화 (REST API 환경에서는 사용하지 않음)
                .formLogin(form -> form.disable());
        // 최종 SecurityFilterChain 반환
        return http.build();
    }

    private List<RequestMatcher> protectedEndpointMatchers() {
        return Arrays.stream(PROTECTED_ENDPOINTS)
                .map(AntPathRequestMatcher::new)
                .collect(Collectors.toUnmodifiableList());
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
