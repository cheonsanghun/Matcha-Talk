package net.datasa.project01.service.email; // 이메일 관련 서비스 클래스가 모여있는 패키지 선언

import lombok.RequiredArgsConstructor; // final 필드 자동 생성자 생성 어노테이션
import org.springframework.beans.factory.annotation.Value; // 프로퍼티 값 주입 어노테이션
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile; // 특정 profile에서만 활성화할 때 사용
import org.springframework.mail.javamail.JavaMailSender; // 스프링 메일 전송 인터페이스
import org.springframework.mail.javamail.MimeMessageHelper; // 메일 메시지 작성 도우미 클래스
import org.springframework.stereotype.Component; // 스프링 컴포넌트(빈)로 등록하는 어노테이션
import jakarta.mail.internet.MimeMessage; // MIME 형식 이메일 메시지 클래스

/**
 * SmtpEmailSender 클래스
 * - 실제 SMTP 서버로 메일을 보내는 구현체입니다.
 * - "db" 프로필에서만 활성화됩니다.
 */
@Component // 스프링이 관리하는 컴포넌트(빈)로 등록
@Profile("db") // "db" 프로필일 때만 활성화됨
@RequiredArgsConstructor // final 필드(mailSender)에 대한 생성자 자동 생성
@ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false)
public class SmtpEmailSender implements EmailSender { // EmailSender 인터페이스 구현

    // 실제 메일 전송을 담당하는 스프링의 JavaMailSender (생성자 주입)
    private final JavaMailSender mailSender;

    // 메일 발신자 정보 (application.yml 등에서 설정, 기본값 제공)
    @Value("${app.mail.from:Matcha Talk <no-reply@localhost>}")
    private String from;

    /**
     * 이메일을 실제로 SMTP 서버를 통해 발송하는 메서드입니다.
     * @param to 수신자 이메일 주소
     * @param subject 이메일 제목
     * @param html 이메일 본문(HTML 형식)
     */
    @Override
    public void send(String to, String subject, String html) {
        try {
            // 빈 MIME 메시지 객체 생성
            MimeMessage message = mailSender.createMimeMessage();
            // 메시지 작성 도우미 객체 생성 (첨부파일 허용, UTF-8 인코딩)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from); // 발신자 설정
            helper.setTo(to); // 수신자 설정
            helper.setSubject(subject); // 제목 설정
            helper.setText(html, true); // 본문 설정 (true: HTML 형식)
            mailSender.send(message); // 메일 전송
        } catch (Exception e) {
            // 예외 발생 시 런타임 예외로 감싸서 던짐
            throw new RuntimeException("메일 전송 실패", e);
        }
    }
}