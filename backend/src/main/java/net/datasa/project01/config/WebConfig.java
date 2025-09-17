package net.datasa.project01.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 애플리케이션의 전역 CORS(Cross-Origin Resource Sharing) 설정을 담당합니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 애플리케이션의 모든 경로에 대해 CORS 설정을 적용합니다.
                // ngrok 주소, localhost의 모든 포트, 그리고 Vue 개발 서버 주소를 명시적으로 허용합니다.
                .allowedOriginPatterns("https://*.ngrok-free.app", "http://localhost:*", "http://localhost:5173") 
                .allowedMethods("*") // 모든 HTTP 메소드(GET, POST, PUT 등)를 허용합니다.
                .allowedHeaders("*") // 모든 헤더를 허용합니다.
                .allowCredentials(true); // 인증 정보(쿠키, JWT 등)를 포함한 요청을 허용합니다.
    }
}