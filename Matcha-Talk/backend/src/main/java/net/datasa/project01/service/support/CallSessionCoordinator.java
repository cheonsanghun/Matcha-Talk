package net.datasa.project01.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 간단한 메모리 기반 영상 통화 준비 상태 조율기.
 * 방 단위로 준비 완료 여부를 추적하여 두 사용자가 모두 준비되었을 때를 감지한다.
 */
@Component
public class CallSessionCoordinator {

    private final Map<Long, CallHandshake> handshakes = new ConcurrentHashMap<>();

    public CallHandshakeStatus markReady(Long roomId, String loginId, Collection<String> participants) {
        if (roomId == null) {
            return CallHandshakeStatus.empty();
        }

        CallHandshake handshake = handshakes.computeIfAbsent(roomId, key -> new CallHandshake());
        return handshake.markReady(loginId, participants);
    }

    public CallHandshakeStatus reset(Long roomId) {
        if (roomId == null) {
            return CallHandshakeStatus.empty();
        }
        CallHandshake handshake = handshakes.remove(roomId);
        if (handshake == null) {
            return CallHandshakeStatus.empty();
        }
        return handshake.snapshot();
    }

    public CallHandshakeStatus peek(Long roomId) {
        if (roomId == null) {
            return CallHandshakeStatus.empty();
        }
        CallHandshake handshake = handshakes.get(roomId);
        if (handshake == null) {
            return CallHandshakeStatus.empty();
        }
        return handshake.snapshot();
    }

    private static final class CallHandshake {
        private Map<String, String> participants = new LinkedHashMap<>();
        private final Set<String> readyParticipants = new LinkedHashSet<>();

        synchronized CallHandshakeStatus markReady(String loginId, Collection<String> participantLogins) {
            updateParticipants(participantLogins);
            if (StringUtils.hasText(loginId)) {
                readyParticipants.add(normalize(loginId));
            }
            return snapshot();
        }

        synchronized void updateParticipants(Collection<String> participantLogins) {
            Map<String, String> next = new LinkedHashMap<>();
            if (participantLogins != null) {
                for (String loginId : participantLogins) {
                    if (!StringUtils.hasText(loginId)) {
                        continue;
                    }
                    String normalized = normalize(loginId);
                    next.putIfAbsent(normalized, loginId);
                }
            }
            participants = next;
            readyParticipants.retainAll(participants.keySet());
        }

        synchronized CallHandshakeStatus snapshot() {
            List<String> participantList = new ArrayList<>(participants.values());
            List<String> readyList = new ArrayList<>();
            for (String normalized : readyParticipants) {
                String original = participants.get(normalized);
                if (original != null) {
                    readyList.add(original);
                }
            }
            boolean allReady = !participants.isEmpty() && readyParticipants.containsAll(participants.keySet());
            return new CallHandshakeStatus(participantList, readyList, allReady);
        }

        private String normalize(String loginId) {
            return loginId == null ? null : loginId.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record CallHandshakeStatus(List<String> participants, List<String> readyMembers, boolean allReady) {
        public static CallHandshakeStatus empty() {
            return new CallHandshakeStatus(List.of(), List.of(), false);
        }
    }
}

