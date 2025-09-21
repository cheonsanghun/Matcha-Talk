package net.datasa.project01.config;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

/**
 * [SecurityConfig]
 * - JWT 기반 인증을 적용해 대부분의 API 접근에 인증을 요구한다.
 * - 로그인/회원가입, 이메일 인증 등 공개 API 는 허용하고 그 외 요청은 보호한다.
 * - 세션은 STATELESS 로 유지하고, CSRF/FormLogin/HttpBasic 을 비활성화한다.
 * - 비밀번호 저장 시 사용할 PasswordEncoder 빈을 제공한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * SecurityFilterChain 빈 등록
     * - REST API에 맞춰 세션을 STATELESS 로 구성
     * - 로그인/회원가입, 헬스체크, 이메일 인증 요청 등 공개가 필요한 엔드포인트만 허용
     * - 그 외 요청은 JWT 인증이 필요하도록 설정
     * - 기본 로그인 폼과 HTTP Basic 은 비활성화
     * - JWT 필터를 UsernamePasswordAuthenticationFilter 앞단에 삽입
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/", "/index.html",
                                "/ws-stomp/**",
                                "/actuator/**",
                                "/api/auth/**",
                                "/api/users/ping",
                                "/api/users/signup",
                                "/api/users/exists",
                                "/api/users/email/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handler -> handler.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
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