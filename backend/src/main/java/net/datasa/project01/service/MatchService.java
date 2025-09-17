package net.datasa.project01.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchDecisionResponseDto;
import net.datasa.project01.domain.dto.MatchEventMessage;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
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
     */
    public MatchStartResponseDto startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 이미 대기열에 있는지 확인
        Optional<MatchRequest> existingWaiting = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING);
        if (existingWaiting.isPresent()) {
            long waitingCount = matchRequestRepository.countByStatusExcludingUser(MatchRequest.MatchStatus.WAITING, me);
            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.ALREADY_WAITING)
                    .myRequestId(existingWaiting.get().getRequestId())
                    .waitingCount(waitingCount)
                    .message(waitingCount > 0 ? "다른 사용자를 찾고 있습니다." : "현재 대기 중인 사용자가 없습니다.")
                    .shouldCreateOffer(false)
                    .build();
        }

        // 2. 이미 매칭된 기록이 있는지 확인
        Optional<MatchRequest> existingMatched = matchRequestRepository.findFirstByUserAndStatusOrderByRequestedAtDesc(me, MatchRequest.MatchStatus.MATCHED);
        if (existingMatched.isPresent() && existingMatched.get().getRoom() != null) {
            MatchRequest myMatched = existingMatched.get();
            MatchRequest opponent = findOpponentRequest(myMatched.getRoom(), myMatched.getRequestId());
            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.MATCHED)
                    .myRequestId(myMatched.getRequestId())
                    .partnerRequestId(opponent != null ? opponent.getRequestId() : null)
                    .roomId(myMatched.getRoom().getRoomId())
                    .partnerLoginId(opponent != null ? opponent.getUser().getLoginId() : null)
                    .partnerNickName(opponent != null ? opponent.getUser().getNickName() : null)
                    .message("이미 진행 중인 매칭이 있습니다.")
                    .shouldCreateOffer(false)
                    .build();
        }

        MatchRequest.Gender myChoiceGender = MatchRequest.Gender.valueOf(requestDto.getChoiceGender());
        Character myChoiceGenderChar = requestDto.getChoiceGender().charAt(0);

        // 3. 나의 조건에 맞는 잠재적 매칭 상대 목록 조회
        List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                myChoiceGenderChar,
                requestDto.getMinAge(),
                requestDto.getMaxAge(),
                MatchRequest.MatchStatus.WAITING
        );

        MatchRequest matchedOpponentRequest = selectFinalOpponent(me, potentialMatches);

        if (matchedOpponentRequest != null) {
            // 4. 매칭 성공 처리
            User opponent = matchedOpponentRequest.getUser();
            log.info("Match found for user {}: {}", loginId, opponent.getLoginId());

            matchedOpponentRequest.setStatus(MatchRequest.MatchStatus.MATCHED);

            // 1:1 채팅방 생성 및 각 요청에 연결
            Room privateRoom = chatService.createPrivateRoom(me, opponent);
            matchedOpponentRequest.setRoom(privateRoom);

            MatchRequest myMatchedRequest = matchRequestRepository.save(MatchRequest.builder()
                    .user(me)
                    .choiceGender(myChoiceGender)
                    .minAge(requestDto.getMinAge())
                    .maxAge(requestDto.getMaxAge())
                    .regionCode(requestDto.getRegionCode())
                    .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                    .status(MatchRequest.MatchStatus.MATCHED)
                    .room(privateRoom)
                    .build());

            // 양쪽 사용자에게 매칭 성공 알림 전송 (웹소켓)
            MatchEventMessage initiatorEvent = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.MATCH_FOUND)
                    .roomId(privateRoom.getRoomId())
                    .myRequestId(myMatchedRequest.getRequestId())
                    .partnerRequestId(matchedOpponentRequest.getRequestId())
                    .partnerLoginId(opponent.getLoginId())
                    .partnerNickName(opponent.getNickName())
                    .message(String.format("%s님과 매칭되었습니다.", opponent.getNickName()))
                    .shouldCreateOffer(true)
                    .build();
            sendMatchEvent(me, initiatorEvent);

            MatchEventMessage opponentEvent = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.MATCH_FOUND)
                    .roomId(privateRoom.getRoomId())
                    .myRequestId(matchedOpponentRequest.getRequestId())
                    .partnerRequestId(myMatchedRequest.getRequestId())
                    .partnerLoginId(me.getLoginId())
                    .partnerNickName(me.getNickName())
                    .message(String.format("%s님과 매칭되었습니다.", me.getNickName()))
                    .shouldCreateOffer(false)
                    .build();
            sendMatchEvent(opponent, opponentEvent);

            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.MATCHED)
                    .myRequestId(myMatchedRequest.getRequestId())
                    .partnerRequestId(matchedOpponentRequest.getRequestId())
                    .roomId(privateRoom.getRoomId())
                    .partnerLoginId(opponent.getLoginId())
                    .partnerNickName(opponent.getNickName())
                    .message("매칭이 성사되었습니다.")
                    .shouldCreateOffer(true)
                    .build();
        }

        // 5. 매칭 실패 -> 대기열에 등록
        log.info("No match found for user {}. Adding to queue.", loginId);
        MatchRequest newRequest = matchRequestRepository.save(MatchRequest.builder()
                .user(me)
                .choiceGender(myChoiceGender)
                .minAge(requestDto.getMinAge())
                .maxAge(requestDto.getMaxAge())
                .regionCode(requestDto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                .status(MatchRequest.MatchStatus.WAITING)
                .build());

        long waitingCount = matchRequestRepository.countByStatusExcludingUser(MatchRequest.MatchStatus.WAITING, me);

        return MatchStartResponseDto.builder()
                .state(MatchStartResponseDto.MatchState.WAITING)
                .myRequestId(newRequest.getRequestId())
                .waitingCount(waitingCount)
                .message(waitingCount > 0 ? "다른 사용자를 찾고 있습니다." : "현재 대기 중인 사용자가 없습니다.")
                .shouldCreateOffer(false)
                .build();
    }

    public MatchDecisionResponseDto respondToMatch(String loginId, Long requestId, boolean accept) {
        MatchRequest myRequest = matchRequestRepository.findByRequestIdAndUser_LoginId(requestId, loginId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        if (myRequest.getStatus() != MatchRequest.MatchStatus.MATCHED && myRequest.getStatus() != MatchRequest.MatchStatus.CONFIRMED) {
            throw new IllegalStateException("현재 상태에서는 응답할 수 없습니다.");
        }

        Room room = myRequest.getRoom();
        if (room == null) {
            throw new IllegalStateException("매칭 세션 정보가 없습니다.");
        }

        MatchRequest opponentRequest = findOpponentRequest(room, myRequest.getRequestId());

        if (accept) {
            myRequest.setStatus(MatchRequest.MatchStatus.CONFIRMED);
        } else {
            myRequest.setStatus(MatchRequest.MatchStatus.DECLINED);
            if (opponentRequest != null) {
                opponentRequest.setStatus(MatchRequest.MatchStatus.CANCELLED);
                opponentRequest.setRoom(null);
            }
            myRequest.setRoom(null);
        }

        MatchRequest.MatchStatus partnerStatus = opponentRequest != null ? opponentRequest.getStatus() : null;
        boolean partnerAlreadyAccepted = partnerStatus == MatchRequest.MatchStatus.CONFIRMED;
        boolean partnerDeclined = partnerStatus == MatchRequest.MatchStatus.DECLINED || partnerStatus == MatchRequest.MatchStatus.CANCELLED;

        if (opponentRequest != null) {
            MatchEventMessage.EventType eventType = accept
                    ? MatchEventMessage.EventType.PARTNER_ACCEPTED
                    : MatchEventMessage.EventType.PARTNER_DECLINED;

            String notice = accept
                    ? String.format("%s님이 매칭을 수락했습니다.", myRequest.getUser().getNickName())
                    : String.format("%s님이 매칭을 거절했습니다.", myRequest.getUser().getNickName());

            MatchEventMessage event = MatchEventMessage.builder()
                    .eventType(eventType)
                    .roomId(room.getRoomId())
                    .myRequestId(opponentRequest.getRequestId())
                    .partnerRequestId(myRequest.getRequestId())
                    .partnerLoginId(myRequest.getUser().getLoginId())
                    .partnerNickName(myRequest.getUser().getNickName())
                    .message(notice)
                    .shouldCreateOffer(false)
                    .build();
            sendMatchEvent(opponentRequest.getUser(), event);
        }

        boolean bothAccepted = accept && partnerAlreadyAccepted;

        if (bothAccepted && opponentRequest != null) {
            MatchEventMessage bothForMe = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.BOTH_CONFIRMED)
                    .roomId(room.getRoomId())
                    .myRequestId(myRequest.getRequestId())
                    .partnerRequestId(opponentRequest.getRequestId())
                    .partnerLoginId(opponentRequest.getUser().getLoginId())
                    .partnerNickName(opponentRequest.getUser().getNickName())
                    .message("서로 매칭을 수락했습니다. 대화를 시작하세요!")
                    .shouldCreateOffer(false)
                    .build();
            sendMatchEvent(myRequest.getUser(), bothForMe);

            MatchEventMessage bothForOpponent = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.BOTH_CONFIRMED)
                    .roomId(room.getRoomId())
                    .myRequestId(opponentRequest.getRequestId())
                    .partnerRequestId(myRequest.getRequestId())
                    .partnerLoginId(myRequest.getUser().getLoginId())
                    .partnerNickName(myRequest.getUser().getNickName())
                    .message("서로 매칭을 수락했습니다. 대화를 시작하세요!")
                    .shouldCreateOffer(false)
                    .build();
            sendMatchEvent(opponentRequest.getUser(), bothForOpponent);
        }

        String responseMessage;
        if (accept) {
            if (partnerDeclined) {
                responseMessage = "상대가 이미 매칭을 종료했습니다.";
            } else if (bothAccepted) {
                responseMessage = "상대도 이미 수락했습니다. 지금 바로 대화를 시작하세요.";
            } else {
                responseMessage = "매칭을 수락했습니다. 상대의 응답을 기다리는 중입니다.";
            }
        } else {
            responseMessage = "매칭을 거절했습니다.";
        }

        return MatchDecisionResponseDto.builder()
                .decision(accept ? MatchDecisionResponseDto.Decision.ACCEPTED : MatchDecisionResponseDto.Decision.DECLINED)
                .roomId(room.getRoomId())
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(opponentRequest != null ? opponentRequest.getRequestId() : null)
                .myStatus(myRequest.getStatus())
                .partnerStatus(partnerStatus)
                .bothAccepted(bothAccepted)
                .message(responseMessage)
                .build();
    }

    private MatchRequest selectFinalOpponent(User me, List<MatchRequest> potentialMatches) {
        for (MatchRequest opponentRequest : potentialMatches) {
            User opponent = opponentRequest.getUser();
            long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());

            boolean isGenderMatch = opponentRequest.getChoiceGender() == MatchRequest.Gender.A ||
                    opponentRequest.getChoiceGender().name().equals(me.getGender().toString());

            boolean isAgeMatch = myAge >= opponentRequest.getMinAge() && myAge <= opponentRequest.getMaxAge();

            if (isGenderMatch && isAgeMatch) {
                return opponentRequest;
            }
        }
        return null;
    }

    private MatchRequest findOpponentRequest(Room room, Long myRequestId) {
        if (room == null) {
            return null;
        }
        return matchRequestRepository.findByRoom(room).stream()
                .filter(req -> !req.getRequestId().equals(myRequestId))
                .findFirst()
                .orElse(null);
    }

    private void sendMatchEvent(User target, MatchEventMessage message) {
        if (target == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(target.getLoginId(), "/queue/match-results", message);
    }
}
