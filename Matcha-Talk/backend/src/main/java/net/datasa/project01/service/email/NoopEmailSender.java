package net.datasa.project01.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 실제 메일 발송 설정이 존재하지 않을 때 기본으로 사용되는 더미 구현입니다.
 */
@Slf4j
@Component
@Profile("!db & !mock")
public class NoopEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String content) {
        log.debug("메일 발송이 구성되지 않아 메시지를 저장하지 않았습니다. to={}, subject={}", to, subject);
    }
}
