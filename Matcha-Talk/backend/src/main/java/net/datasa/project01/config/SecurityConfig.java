package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.config.handler.RestAccessDeniedHandler;
import net.datasa.project01.config.handler.RestAuthenticationEntryPoint;
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
 * - JWT 기반 인증을 적용하고 REST API 환경에 맞춰 CSRF, 폼 로그인 등을 비활성화합니다.
 * - 인증이 필요 없는 공개 엔드포인트(/error, WebSocket 등)는 permitAll로 예외 처리합니다.
 * - 인증 실패/권한 부족 시 일관된 JSON 응답을 내려주도록 커스텀 핸들러를 등록합니다.
 * - 비밀번호 저장 시 BCrypt 해시를 사용하도록 PasswordEncoder 빈을 제공합니다.
 */
@Configuration // 스프링 설정 클래스임을 명시
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

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
                        // WebSocket 연결 경로 허용 - SockJS 폴백 포함
                        .requestMatchers("/ws-stomp/**").permitAll()
                        .requestMatchers("/ws-stomp/*/xhr_streaming").permitAll()
                        .requestMatchers("/ws-stomp/*/websocket").permitAll()
                        .requestMatchers("/ws-stomp/info").permitAll()
                        // 회원가입, 로그인, 중복확인, 이메일 인증 등 인증 없이 접근해야 하는 경로 허용
                        .requestMatchers("/api/auth/login", "/api/auth/find-id", "/api/auth/password-reset/**", "/api/users/signup", "/api/users/exists", "/api/users/email/**").permitAll()
                        // 오류 및 상태 체크 엔드포인트는 인증 없이 접근 허용
                        .requestMatchers("/error", "/actuator/health", "/actuator/info").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // 폼 로그인 비활성화 (REST API 환경에서는 사용하지 않음)
                .formLogin(form -> form.disable())
                // HTTP Basic 인증 비활성화
                .httpBasic(httpBasic -> httpBasic.disable())
                // 인증/인가 예외 시 커스텀 JSON 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
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
