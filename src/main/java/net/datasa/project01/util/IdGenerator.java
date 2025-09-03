package net.datasa.project01.util;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단순 증가 시퀀스.
 * - 시작값(1000L)은 팀 규칙에 맞게 조정 가능.
 * - DB AUTO_INCREMENT가 아니므로, 앱에서 PK를 넣어주기 위한 용도.
 */
@Component
public class IdGenerator {
    private final AtomicLong seq = new AtomicLong(1000L);
    public long nextId() { return seq.incrementAndGet(); }
}
