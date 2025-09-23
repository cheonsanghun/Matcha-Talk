package net.datasa.project01.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchDecisionResponseDto;
import net.datasa.project01.domain.dto.MatchEventMessage;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    @PersistenceContext
    private EntityManager entityManager;

    private static final Duration WAITING_TIMEOUT = Duration.ofMinutes(5);

    /**
     * 랜덤 매칭을 시작하거나 대기열에서 상대를 찾기
     * @param loginId 요청한 사용자의 ID
     * @param requestDto 매칭 조건
     */
    public MatchStartResponseDto startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 이미 대기열에 있는지 확인
        LocalDateTime now = LocalDateTime.now();

        Optional<MatchRequest> existingWaiting = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING);
        if (existingWaiting.isPresent()) {
            MatchRequest waitingRequest = existingWaiting.get();
            if (isRequestExpired(waitingRequest, now)) {
                cancelExpiredRequest(waitingRequest);
            } else {
                long waitingCount = matchRequestRepository.countByStatusExcludingUser(MatchRequest.MatchStatus.WAITING, me);
                return MatchStartResponseDto.builder()
                        .state(MatchStartResponseDto.MatchState.ALREADY_WAITING)
                        .myRequestId(waitingRequest.getRequestId())
                        .waitingCount(waitingCount)
                        .message(waitingCount > 0 ? "다른 사용자를 찾고 있습니다." : "현재 대기 중인 사용자가 없습니다.")
                        .shouldCreateOffer(false)
                        .myStatus(waitingRequest.getStatus())
                        .partnerStatus(null)
                        .bothAccepted(false)
                        .build();
            }
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
                    .myStatus(myMatched.getStatus())
                    .partnerStatus(opponent != null ? opponent.getStatus() : null)
                    .bothAccepted(opponent != null
                            && myMatched.getStatus() == MatchRequest.MatchStatus.CONFIRMED
                            && opponent.getStatus() == MatchRequest.MatchStatus.CONFIRMED)
                    .build();
        }

        MatchRequest.Gender myChoiceGender = MatchRequest.Gender.valueOf(requestDto.getChoiceGender());
        Character myChoiceGenderChar = requestDto.getChoiceGender().charAt(0);

        if (requestDto.getMinAge() > requestDto.getMaxAge()) {
            throw new IllegalArgumentException("최소 나이는 최대 나이보다 클 수 없습니다.");
        }

        LocalDate today = LocalDate.now();
        LocalDate oldestBirthDate = today.minusYears(requestDto.getMaxAge());
        LocalDate youngestBirthDate = today.minusYears(requestDto.getMinAge());

        List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                requestDto.getChoiceGender(),
                requestDto.getRegionCode(),
                oldestBirthDate,
                youngestBirthDate,
                MatchRequest.MatchStatus.WAITING
        );

        List<MatchRequest> filteredPotentialMatches = new ArrayList<>();
        for (MatchRequest potentialMatch : potentialMatches) {
            if (isRequestExpired(potentialMatch, now)) {
                cancelExpiredRequest(potentialMatch);
                continue;
            }
            filteredPotentialMatches.add(potentialMatch);
        }

        MatchRequest matchedOpponentRequest = selectFinalOpponent(me, filteredPotentialMatches);

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
                    .myStatus(myMatchedRequest.getStatus())
                    .partnerStatus(matchedOpponentRequest.getStatus())
                    .bothAccepted(false)
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
                .myStatus(newRequest.getStatus())
                .partnerStatus(null)
                .bothAccepted(false)
                .build();
    }

    @Transactional(readOnly = true)
    public MatchStartResponseDto getMatchStatus(String loginId, Long requestId) {
        MatchRequest myRequest = matchRequestRepository.findByRequestIdAndUser_LoginId(requestId, loginId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        MatchRequest.MatchStatus status = myRequest.getStatus();

        if (status == MatchRequest.MatchStatus.WAITING) {
            long waitingCount = matchRequestRepository.countByStatusExcludingUser(MatchRequest.MatchStatus.WAITING, myRequest.getUser());
            String message = waitingCount > 0 ? "다른 사용자를 찾고 있습니다." : "현재 대기 중인 사용자가 없습니다.";

            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.WAITING)
                    .myRequestId(myRequest.getRequestId())
                    .waitingCount(waitingCount)
                    .message(message)
                    .shouldCreateOffer(false)
                    .myStatus(myRequest.getStatus())
                    .partnerStatus(null)
                    .bothAccepted(false)
                    .build();
        }

        if (status == MatchRequest.MatchStatus.MATCHED || status == MatchRequest.MatchStatus.CONFIRMED) {
            MatchRequest opponent = findOpponentRequest(myRequest.getRoom(), myRequest.getRequestId());

            boolean shouldCreateOffer = shouldCreateOffer(myRequest, opponent);
            MatchRequest.MatchStatus partnerStatus = opponent != null ? opponent.getStatus() : null;
            boolean bothAccepted = status == MatchRequest.MatchStatus.CONFIRMED
                    && partnerStatus == MatchRequest.MatchStatus.CONFIRMED;

            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.MATCHED)
                    .myRequestId(myRequest.getRequestId())
                    .partnerRequestId(opponent != null ? opponent.getRequestId() : null)
                    .roomId(myRequest.getRoom() != null ? myRequest.getRoom().getRoomId() : null)
                    .partnerLoginId(opponent != null ? opponent.getUser().getLoginId() : null)
                    .partnerNickName(opponent != null ? opponent.getUser().getNickName() : null)
                    .message("매칭이 성사되었습니다.")
                    .shouldCreateOffer(shouldCreateOffer)
                    .myStatus(myRequest.getStatus())
                    .partnerStatus(partnerStatus)
                    .bothAccepted(bothAccepted)
                    .build();
        }

        String message = switch (status) {
            case DECLINED, CANCELLED -> "매칭이 종료되었습니다.";
            default -> "현재 매칭 상태를 확인할 수 없습니다.";
        };

        return MatchStartResponseDto.builder()
                .state(MatchStartResponseDto.MatchState.WAITING)
                .myRequestId(myRequest.getRequestId())
                .message(message)
                .shouldCreateOffer(false)
                .myStatus(myRequest.getStatus())
                .partnerStatus(null)
                .bothAccepted(false)
                .build();
    }

    public MatchDecisionResponseDto respondToMatch(String loginId, Long requestId, boolean accept) {
        MatchRequest snapshot = matchRequestRepository.findByRequestIdAndUser_LoginId(requestId, loginId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        Room room = snapshot.getRoom();
        if (room == null) {
            throw new IllegalStateException("매칭 세션 정보가 없습니다.");
        }

        List<MatchRequest> roomParticipants = matchRequestRepository.findAllByRoomForUpdate(room);

        MatchRequest myRequest = roomParticipants.stream()
                .filter(request -> request.getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        if (!myRequest.getUser().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("해당 매칭 요청에 대한 권한이 없습니다.");
        }

        if (myRequest.getStatus() != MatchRequest.MatchStatus.MATCHED && myRequest.getStatus() != MatchRequest.MatchStatus.CONFIRMED) {
            throw new IllegalStateException("현재 상태에서는 응답할 수 없습니다.");
        }

        MatchRequest opponentRequest = roomParticipants.stream()
                .filter(request -> !request.getRequestId().equals(requestId))
                .findFirst()
                .orElse(null);

        if (accept) {
            myRequest.setStatus(MatchRequest.MatchStatus.CONFIRMED);
            entityManager.flush();
            if (opponentRequest != null) {
                entityManager.refresh(opponentRequest);
            }
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
        boolean bothAccepted = accept && partnerAlreadyAccepted;

        boolean shouldCreateOfferForMe = shouldCreateOffer(myRequest, opponentRequest);
        boolean shouldCreateOfferForOpponent = shouldCreateOffer(opponentRequest, myRequest);

        if (opponentRequest != null && (!bothAccepted || !accept)) {
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

        if (bothAccepted && opponentRequest != null) {
            MatchEventMessage bothForMe = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.BOTH_CONFIRMED)
                    .roomId(room.getRoomId())
                    .myRequestId(myRequest.getRequestId())
                    .partnerRequestId(opponentRequest.getRequestId())
                    .partnerLoginId(opponentRequest.getUser().getLoginId())
                    .partnerNickName(opponentRequest.getUser().getNickName())
                    .message("서로 매칭을 수락했습니다. 대화를 시작하세요!")
                    .shouldCreateOffer(shouldCreateOfferForMe)
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
                    .shouldCreateOffer(shouldCreateOfferForOpponent)
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
        long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());
        for (MatchRequest opponentRequest : potentialMatches) {

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

    private boolean shouldCreateOffer(MatchRequest current, MatchRequest opponent) {
        if (current == null) {
            return false;
        }
        if (opponent == null) {
            return true;
        }

        LocalDateTime currentRequestedAt = current.getRequestedAt();
        LocalDateTime opponentRequestedAt = opponent.getRequestedAt();

        if (currentRequestedAt != null && opponentRequestedAt != null) {
            if (currentRequestedAt.isAfter(opponentRequestedAt)) {
                return true;
            }
            if (currentRequestedAt.isBefore(opponentRequestedAt)) {
                return false;
            }
        }

        Long currentId = current.getRequestId();
        Long opponentId = opponent.getRequestId();
        if (currentId != null && opponentId != null) {
            return currentId > opponentId;
        }

        return false;
    }

    private void sendMatchEvent(User target, MatchEventMessage message) {
        if (target == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(target.getLoginId(), "/queue/match-results", message);
    }

    private boolean isRequestExpired(MatchRequest request, LocalDateTime referenceTime) {
        if (request == null || request.getRequestedAt() == null) {
            return false;
        }
        LocalDateTime expiryThreshold = referenceTime.minus(WAITING_TIMEOUT);
        return request.getRequestedAt().isBefore(expiryThreshold);
    }

    private void cancelExpiredRequest(MatchRequest request) {
        if (request == null) {
            return;
        }
        if (request.getStatus() == MatchRequest.MatchStatus.WAITING) {
            log.debug("Cancelling expired waiting request: {}", request.getRequestId());
            request.setStatus(MatchRequest.MatchStatus.CANCELLED);
            request.setRoom(null);
        }
    }
}
