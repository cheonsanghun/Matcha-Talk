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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;

@RequiredArgsConstructor // JWT 필터 등을 주입받을 경우 사용
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private final JwtUtil jwtUtil;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                    // 아래의 경로 패턴들이 올바른 형식인지 다시 한번 확인
                    .requestMatchers(
                        "/",
                        "/login.html", 
                        "/signup.html", 
                        "/chat.html",
                        "/favicon.ico",
                        "/css/**",      // 올바른 패턴
                        "/js/**",       // 올바른 패턴
                        "/images/**",   // 올바른 패턴
                        "/ws-connect/**" // 올바른 패턴
                    ).permitAll()
                    .requestMatchers("/api/users/signup", "/api/users/login").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userDetailsServiceImpl), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}


