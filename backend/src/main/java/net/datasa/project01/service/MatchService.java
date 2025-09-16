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
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 랜덤 매칭을 시작하거나 대기열에서 상대를 찾기
     * @param loginId 요청한 사용자의 ID
     * @param requestDto 매칭 조건
     * 주요 로직들이 다수 들어가있으므로 매우 중요함
     * 작동 안할 시 로직에 대해 다시 고려해볼 것
     */
    public void startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 이미 대기열에 있는지 확인
        Optional<MatchRequest> existingRequest = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING);
        if (existingRequest.isPresent()) {
            log.info("User {} is already in the matching queue.", loginId);
            return; // 이미 대기 중이므로 아무것도 하지 않음
        }

        // 2. 나의 조건에 맞는 잠재적 매칭 상대 목록 조회
        List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                requestDto.getChoiceGender(),
                requestDto.getMinAge(),
                requestDto.getMaxAge(),
                MatchRequest.MatchStatus.WAITING
        );

        // 3. Java 코드로 최종 매칭 상대 결정 (역방향 검증)
        MatchRequest matchedOpponentRequest = null;
        for (MatchRequest opponentRequest : potentialMatches) {
            User opponent = opponentRequest.getUser();
            long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());

            // 상대방의 희망 성별이 '모두(A)'이거나 '나의 성별'과 일치하는지 확인
            boolean isGenderMatch = opponentRequest.getChoiceGender() == MatchRequest.Gender.A ||
                                    opponentRequest.getChoiceGender().name().equals(me.getGender().toString());
            
            // 나의 나이가 상대방의 희망 나이 범위에 속하는지 확인
            boolean isAgeMatch = myAge >= opponentRequest.getMinAge() && myAge <= opponentRequest.getMaxAge();

            if (isGenderMatch && isAgeMatch) {
                matchedOpponentRequest = opponentRequest;
                break; // 첫 번째로 찾은 짝과 매칭
            }
        }

        if (matchedOpponentRequest != null) {
            // 4. 매칭 성공 처리
            User opponent = matchedOpponentRequest.getUser();
            log.info("Match found for user {}: {}", loginId, opponent.getLoginId());

            // 두 요청의 상태를 MATCHED로 변경
            matchedOpponentRequest.setStatus(MatchRequest.MatchStatus.MATCHED);

            // 1:1 채팅방 생성
            Room privateRoom = chatService.createPrivateRoom(me, opponent);

            // 양쪽 사용자에게 매칭 성공 알림 전송 (웹소켓)
            MatchFoundResponseDto myResponse = new MatchFoundResponseDto(privateRoom.getRoomId(), opponent.getNickName());
            MatchFoundResponseDto opponentResponse = new MatchFoundResponseDto(privateRoom.getRoomId(), me.getNickName());

            messagingTemplate.convertAndSendToUser(me.getLoginId(), "/queue/match-results", myResponse);
            messagingTemplate.convertAndSendToUser(opponent.getLoginId(), "/queue/match-results", opponentResponse);

        } else {
            // 5. 매칭 실패 -> 대기열에 등록
            log.info("No match found for user {}. Adding to queue.", loginId);
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
        }
    }
}