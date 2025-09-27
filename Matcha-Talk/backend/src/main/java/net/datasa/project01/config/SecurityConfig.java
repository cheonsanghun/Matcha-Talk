package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.config.handler.RestAccessDeniedHandler;
import net.datasa.project01.config.handler.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * [SecurityConfig]
 * - JWT 기반 인증을 제거하고 모든 REST/WebSocket 요청을 허용합니다.
 * - CSRF, 폼 로그인, HTTP Basic 인증을 비활성화하여 SPA/REST 환경에 맞춥니다.
 * - 인증 실패/권한 부족 시 일관된 JSON 응답을 내려주도록 커스텀 핸들러를 유지합니다.
 * - 비밀번호 저장 시 BCrypt 해시를 사용하도록 PasswordEncoder 빈을 제공합니다.
 */
@Configuration // 스프링 설정 클래스임을 명시
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * SecurityFilterChain 빈 등록
     */
    @Bean
    SecurityFilterChain http(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API나 테스트 환경에서는 불필요)
                .csrf(csrf -> csrf.disable())
                // 세션을 사용하지 않도록 설정 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HTTP 요청 권한 설정
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/ws/chat/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().permitAll()
                )
                // 폼 로그인 비활성화 (REST API 환경에서는 사용하지 않음)
                .formLogin(form -> form.disable())
                // HTTP Basic 인증 비활성화
                .httpBasic(httpBasic -> httpBasic.disable())
                // 인증/인가 예외 시 커스텀 JSON 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

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
