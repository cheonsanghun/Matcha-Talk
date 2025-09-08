package net.datasa.project01.Config;

// import net.datasa.project01.service.UserDetailsServiceImpl;
// import net.datasa.project01.util.JwtUtil;
// import lombok.RequiredArgsConstructor;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.config.http.SessionCreationPolicy;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration
// @EnableWebSecurity
// @RequiredArgsConstructor
// public class SecurityConfig {

//     private final UserDetailsServiceImpl userDetailsService;
//     private final JwtUtil jwtUtil;

//     private static final String[] PUBLIC_ENDPOINTS = {
//         "/",
//         "/index.html",
//         "/login.html",
//         "/chat.html",
//         "/css/**",
//         "/js/**",
//         "/images/**",
//         "/favicon.ico",
//         "/ws-connect/**",
//         "/api/users/signup",
//         "/api/users/login"
//     };

//     @Bean
//     public PasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }

//     @Bean
//     public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//         return http
//             .csrf(csrf -> csrf.disable())
//             .sessionManagement(session -> 
//                 session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//             .authorizeHttpRequests(authorize -> authorize
//                 .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
//                 .anyRequest().authenticated()
//             )
//             .addFilterBefore(
//                 new JwtAuthenticationFilter(jwtUtil, userDetailsService), 
//                 UsernamePasswordAuthenticationFilter.class
//             )
//             .build();
//     }
// }

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// @RequiredArgsConstructor // JWT 필터 등을 주입받을 경우 사용
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 허용할 경로들
    private static final String[] PERMIT_ALL_PATTERNS = {
            // 기본 페이지 및 정적 리소스
            "/",
            "/**/*.html",
            "/**/*.css",
            "/**/*.js",
            "/favicon.ico",
            // API
            "/api/users/signup",
            "/api/users/login",
            // WebSocket
            "/ws-connect/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // CSRF 보호 비활성화
            .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 STATELESS 설정
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PERMIT_ALL_PATTERNS).permitAll() // 허용할 경로 설정
                .anyRequest().authenticated() // 나머지 요청은 인증 필요
            );

        return http.build();
    }
}


