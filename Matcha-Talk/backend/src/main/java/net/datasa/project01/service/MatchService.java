package net.datasa.project01.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchFoundResponseDto;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.websocket.RealTimeMessagingService;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MatchService {

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final RealTimeMessagingService messagingService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String MATCH_LOCK_PREFIX = "match:lock:";
    private static final Duration MATCH_LOCK_TTL = Duration.ofSeconds(15);

    /**
     * 랜덤 매칭을 시작하거나 대기열에서 상대를 찾기
     * @param loginId 요청한 사용자의 ID
     * @param requestDto 매칭 조건
     */
    public void startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!acquireLock(loginId)) {
            log.warn("매칭 락 획득 실패 loginId={}", loginId);
            throw new IllegalStateException("현재 매칭 요청을 처리할 수 없습니다. 잠시 후 다시 시도하세요.");
        }

        try {
            Optional<MatchRequest> existingRequest = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING);
            if (existingRequest.isPresent()) {
                log.info("User {} is already in the matching queue.", loginId);
                sendWaitingNotification(loginId);
                return;
            }

            List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                    me.getUserPid(),
                    MatchRequest.MatchStatus.WAITING
            );

            MatchRequest matchedOpponentRequest = null;
            for (MatchRequest opponentRequest : potentialMatches) {
                User opponent = opponentRequest.getUser();
                long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());

                boolean isGenderMatch = opponentRequest.getChoiceGender() == MatchRequest.Gender.A ||
                        opponentRequest.getChoiceGender().name().equals(me.getGender().toString());
                boolean isAgeMatch = myAge >= opponentRequest.getMinAge() && myAge <= opponentRequest.getMaxAge();

                if (!isGenderMatch || !isAgeMatch) {
                    continue;
                }

                String opponentLoginId = opponent.getLoginId();
                if (!acquireLock(opponentLoginId)) {
                    log.debug("상대방 락 획득 실패 - loginId={}", opponentLoginId);
                    continue;
                }
                try {
                    MatchRequest freshOpponent = matchRequestRepository.findById(opponentRequest.getRequestId())
                            .orElse(null);
                    if (freshOpponent == null || freshOpponent.getStatus() != MatchRequest.MatchStatus.WAITING) {
                        continue;
                    }
                    matchedOpponentRequest = freshOpponent;
                    break;
                } finally {
                    if (matchedOpponentRequest == null) {
                        releaseLock(opponentLoginId);
                    }
                }
            }

            if (matchedOpponentRequest != null) {
                handleMatchedUsers(me, requestDto, matchedOpponentRequest);
            } else {
                enqueueWaitingUser(me, requestDto);
            }
        } finally {
            releaseLock(loginId);
        }
    }

    private void handleMatchedUsers(User me,
                                    MatchRequestDto requestDto,
                                    MatchRequest opponentRequest) throws JsonProcessingException {
        User opponent = opponentRequest.getUser();
        String opponentLoginId = opponent.getLoginId();

        try {
            opponentRequest.setStatus(MatchRequest.MatchStatus.MATCHED);

            MatchRequest myMatchedRequest = MatchRequest.builder()
                    .user(me)
                    .choiceGender(MatchRequest.Gender.valueOf(requestDto.getChoiceGender()))
                    .minAge(requestDto.getMinAge())
                    .maxAge(requestDto.getMaxAge())
                    .regionCode(requestDto.getRegionCode())
                    .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                    .status(MatchRequest.MatchStatus.MATCHED)
                    .build();
            matchRequestRepository.save(myMatchedRequest);

            Room privateRoom = chatService.createPrivateRoom(me, opponent);

            MatchFoundResponseDto myResponse = new MatchFoundResponseDto(privateRoom.getRoomId(), opponent.getNickName());
            MatchFoundResponseDto opponentResponse = new MatchFoundResponseDto(privateRoom.getRoomId(), me.getNickName());

            sendMatchResult(me.getLoginId(), myResponse);
            sendMatchResult(opponentLoginId, opponentResponse);
        } finally {
            releaseLock(opponentLoginId);
        }
    }

    private void enqueueWaitingUser(User me, MatchRequestDto requestDto) throws JsonProcessingException {
        log.info("❌ No match found for user {}. Adding to queue.", me.getLoginId());
        MatchRequest newRequest = MatchRequest.builder()
                .user(me)
                .choiceGender(MatchRequest.Gender.valueOf(requestDto.getChoiceGender()))
                .minAge(requestDto.getMinAge())
                .maxAge(requestDto.getMaxAge())
                .regionCode(requestDto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                .status(MatchRequest.MatchStatus.WAITING)
                .build();
        matchRequestRepository.save(newRequest);

        sendWaitingNotification(me.getLoginId());
    }

    private boolean acquireLock(String loginId) {
        String key = MATCH_LOCK_PREFIX + loginId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", MATCH_LOCK_TTL);
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock(String loginId) {
        stringRedisTemplate.delete(MATCH_LOCK_PREFIX + loginId);
    }
    
    /**
     * ✅ 매칭 결과 전송 (공통 메서드)
     */
    private void sendMatchResult(String loginId, MatchFoundResponseDto response) {
        try {
            log.info("🚀 Sending match result to user: {}", loginId);
            
            // 사용자별 큐로 전송
            messagingService.sendEventToUser(loginId, "match-result", response);
            
            log.info("✅ Match result sent successfully to: {}", loginId);
        } catch (Exception e) {
            log.error("❌ Failed to send match result to user {}: {}", loginId, e.getMessage());
        }
    }
    
    /**
     * ✅ 테스트용: 대기 상태 알림
     */
    private void sendWaitingNotification(String loginId) {
        try {
            log.info("📋 Sending waiting notification to user: {}", loginId);
            
            String waitingMessage = "매칭 대기 중입니다. 상대방을 찾고 있어요...";
            
            // 사용자별 큐로 전송
            messagingService.sendEventToUser(loginId, "match-status", waitingMessage);
            
            log.info("✅ Waiting notification sent to: {}", loginId);
        } catch (Exception e) {
            log.error("❌ Failed to send waiting notification to user {}: {}", loginId, e.getMessage());
        }
    }
}
