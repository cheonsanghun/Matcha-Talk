package net.datasa.project01.service.email; // 이메일 관련 클래스가 모여있는 패키지 선언

import org.slf4j.Logger; // SLF4J 로깅 인터페이스 import (로그 출력용)
import org.slf4j.LoggerFactory; // Logger 객체 생성 도우미 클래스 import
import org.springframework.context.annotation.Profile; // 특정 profile에서만 활성화할 때 사용
import org.springframework.stereotype.Component; // 스프링 컴포넌트(빈)로 등록하는 어노테이션

/**
 * MockEmailSender 클래스
 * - 실제로 메일을 보내지 않고, 로그로만 출력하는 테스트용(모의) 이메일 발송 클래스입니다.
 * - "mock" 프로필에서만 활성화됩니다.
 * - Postman 테스트에서는 응답 JSON에 dev_token을 같이 내려줍니다.
 */
@Component // 스프링이 관리하는 컴포넌트(빈)로 등록
@Profile("mock") // "mock" 프로필일 때만 활성화됨
public class MockEmailSender implements EmailSender { // EmailSender 인터페이스 구현

    // Logger 객체 생성 (이 클래스의 로그를 남기기 위해 사용)
    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    /**
     * 이메일 발송 메서드 (테스트용)
     * - 실제로 메일을 보내지 않고, 로그로만 내용을 출력합니다.
     * @param to 수신자 이메일 주소
     * @param subject 이메일 제목
     * @param content 이메일 본문(HTML 또는 텍스트)
     */
    @Override
    public void send(String to, String subject, String content) {
        // 로그에 이메일 발송 정보를 출력 (실제 발송은 하지 않음)
        log.info("[MOCK-MAIL] to={}, subject={}, content=\n{}", to, subject, content);
    }
}