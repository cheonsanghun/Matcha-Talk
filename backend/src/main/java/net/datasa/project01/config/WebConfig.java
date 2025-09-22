package net.datasa.project01.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Global CORS configuration for the application.
 * Allows the frontend running on localhost:5181 to access backend APIs.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${chat.files.storage-path:uploads/chat}")
    private String storagePath;

    @Value("${chat.files.public-url-prefix:/files/chat}")
    private String publicUrlPrefix;
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                //.allowedOriginPatterns("https://*.ngrok-free.app","http://localhost:*")
                .allowedOriginPatterns(
                        "https://*.ngrok-free.app",
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://192.168.*.*:*",
                        "https://192.168.*.*:*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = publicUrlPrefix;
        if (!pattern.endsWith("/")) {
            pattern += "/";
        }
        String location = Path.of(storagePath).toUri().toString();
        registry.addResourceHandler(pattern + "**")
                .addResourceLocations(location);
    }
}