package net.datasa.project01;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import lombok.extern.slf4j.Slf4j;

// TODO: 환경별 프로파일 설정 추가 고려
// @Profile("!test")
@SpringBootApplication
@EnableJpaAuditing // JPA Auditing 기능 활성화
@Slf4j
public class Project01Application {

	public static void main(String[] args) {
		// TODO: 애플리케이션 시작 전 환경 검증 추가
		// validateEnvironment();
		
		try {
			SpringApplication app = new SpringApplication(Project01Application.class);
			// TODO: 배너 커스터마이징 추가
			// app.setBanner(new CustomBanner());
			app.run(args);
		} catch (Exception e) {
			log.error("=== Application startup failed ===");
			if (e.getMessage() != null && e.getMessage().contains("Communications link failure")) {
				log.error("MySQL Database connection failed!");
				log.error("Possible solutions:");
				log.error("1. Start MySQL server");
				log.error("2. Check MySQL is running on port 3306");
				log.error("3. Verify database 'matcha_talk_db' exists");
				log.error("4. Check username/password in application.properties");
				log.error("5. Use H2 database instead: --spring.profiles.active=h2");
			}
			log.error("Full error: ", e);
			System.exit(1);
		}
	}
	
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		log.info("===============================================");
		log.info(" MatchTalk Application Started Successfully! ");
		log.info(" Server is running on port: 9999");
		log.info(" Swagger UI: http://localhost:9999/swagger-ui.html (TODO)");
		log.info(" Health Check: http://localhost:9999/actuator/health");
		log.info("===============================================");
	}
	
	// TODO: 애플리케이션 종료 이벤트 핸들러 추가
	// @EventListener(ContextClosedEvent.class)
	// public void onApplicationShutdown() { }

}
