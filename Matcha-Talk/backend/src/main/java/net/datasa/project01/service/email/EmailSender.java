package net.datasa.project01.service.email; // 이메일 관련 서비스 클래스가 모여있는 패키지 선언

/**
 * EmailSender 인터페이스
 * - 이메일을 발송하는 기능을 정의합니다.
 * - 실제 구현체는 SMTP, Mock 등 다양한 방식으로 만들 수 있습니다.
 * - 이 인터페이스를 구현한 클래스에서 send() 메서드를 구체적으로 작성해야 합니다.
 */
public interface EmailSender { // 이메일 발송 기능을 위한 인터페이스 선언

    /**
     * 이메일을 발송하는 메서드입니다.
     * @param to 수신자 이메일 주소 (예: user@example.com)
     * @param subject 이메일 제목 (예: "회원가입 인증")
     * @param contentHtmlOrText 이메일 본문 내용 (HTML 또는 일반 텍스트)
     *
     * 구현체에서는 이 정보를 이용해 실제로 이메일을 전송합니다.
     */
    void send(String to, String subject, String contentHtmlOrText); // 이메일 발송 메서드 (구현 필요)

}