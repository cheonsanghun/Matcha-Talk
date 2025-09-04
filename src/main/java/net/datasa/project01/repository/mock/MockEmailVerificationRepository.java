package net.datasa.project01.repository.mock;

import net.datasa.project01.domain.entity.EmailVerification;
import net.datasa.project01.domain.vo.VerificationPurpose;
import net.datasa.project01.repository.EmailVerificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [MOCK 저장소]
 * - DB 대신 자바 Map으로 email_verifications 동작을 흉내냄
 * - 서버 종료 시 데이터는 모두 사라짐
 * - 목적: db 없어도 Postman으로 토큰 발급/검증 흐름 개발/테스트 가능하게
 */
@Repository
@Profile("mock") // mock 프로필에서만 활성
public class MockEmailVerificationRepository implements EmailVerificationRepository {

    /** 의사 PK 시퀀스 (AUTO_INCREMENT 흉내) */
    private long seq = 1L;

    /** token_id -> EmailVerification */
    private final Map<Long, EmailVerification> store = new ConcurrentHashMap<>();
    /** token(문자열) -> token_id  (UNIQUE 인덱스 흉내) */
    private final Map<String, Long> byToken = new ConcurrentHashMap<>();
    /** (userPid + purpose) -> token_id 목록 (최신 추가) */
    private final Map<String, List<Long>> byUserPurpose = new ConcurrentHashMap<>();

    /** user+purpose 키 생성기 */
    private String keyUP(Long userPid, VerificationPurpose purpose) {
        return userPid + "_" + purpose.name();
    }

    /** INSERT/UPDATE 공통 저장 */
    @Override
    public synchronized EmailVerification save(EmailVerification ev) {
        if (ev.getTokenId() == null) {
            ev.setTokenId(seq++); // 새 ID 발급
            if (ev.getCreatedAt() == null) {
                ev.setCreatedAt(LocalDateTime.now()); // DB default 대체
            }
        }
        store.put(ev.getTokenId(), ev);
        byToken.put(ev.getToken(), ev.getTokenId());
        byUserPurpose
                .computeIfAbsent(keyUP(ev.getUser().getUserPid(), ev.getPurpose()), k -> new ArrayList<>())
                .add(ev.getTokenId());
        return ev;
    }

    /** 토큰 + 목적 일치하는 1건 조회 (검증 시 사용) */
    @Override
    public Optional<EmailVerification> findByTokenAndPurpose(String token, VerificationPurpose purpose) {
        Long id = byToken.get(token);
        if (id == null) return Optional.empty();
        EmailVerification ev = store.get(id);
        if (ev == null || ev.getPurpose() != purpose) return Optional.empty();
        return Optional.of(ev);
    }

    /** 특정 사용자/목적의 '미사용 & 만료 전' 최신 토큰 1건 (쿨다운/재사용 체크용) */
    @Override
    public Optional<EmailVerification> findLatestActiveByUserAndPurpose(Long userPid,
                                                                        VerificationPurpose purpose,
                                                                        LocalDateTime now) {
        List<Long> ids = byUserPurpose.getOrDefault(keyUP(userPid, purpose), List.of());
        // 뒤에서부터(최근 발급) 검사
        ListIterator<Long> it = ids.listIterator(ids.size());
        while (it.hasPrevious()) {
            EmailVerification ev = store.get(it.previous());
            if (ev != null && ev.getUsedAt() == null && ev.getExpiresAt().isAfter(now)) {
                return Optional.of(ev);
            }
        }
        return Optional.empty();
    }

    /** 만료 토큰 일괄 삭제(스케줄러가 주기적으로 호출) */
    @Override
    public long deleteExpired(LocalDateTime now) {
        long removed = 0;
        for (Iterator<Map.Entry<Long, EmailVerification>> it = store.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            EmailVerification ev = e.getValue();
            if (!ev.getExpiresAt().isAfter(now)) { // now 이상이면 만료
                it.remove();
                byToken.remove(ev.getToken());
                var key = keyUP(ev.getUser().getUserPid(), ev.getPurpose());
                var list = byUserPurpose.get(key);
                if (list != null) list.remove(e.getKey());
                removed++;
            }
        }
        return removed;
    }
}