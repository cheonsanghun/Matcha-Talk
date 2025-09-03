package net.datasa.project01.Config;

import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
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
                        .requestMatchers(
                            "/",
                            "/login.html", 
                            "/signup.html", 
                            "/chat.html", // 테스트용 열어놓을 놈들 추가해뒀음
                            "/css/**", 
                            "/js/**", 
                            "/images/**",
                            "/favicon.ico", // favicon.ico 허용
                            "/ws-connect/**" // 웹소켓 연결 경로 및 하위 경로 모두 허용
                            ).permitAll()
                            // 원래 열어놔야될 놈들은 아래
                            .requestMatchers("/api/users/signup", "/api/users/login").permitAll()
                            // 그 외 요청은 모두 인증 거쳐야됨
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userDetailsServiceImpl), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
