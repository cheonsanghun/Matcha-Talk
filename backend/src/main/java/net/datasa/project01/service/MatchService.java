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

    public MatchStartResponseDto startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MatchRequest existingMatch = findLatestByStatuses(me,
                List.of(MatchRequest.MatchStatus.MATCHED, MatchRequest.MatchStatus.CONFIRMED));
        if (existingMatch != null) {
            MatchRequest partnerRequest = findPartnerRequest(existingMatch).orElse(null);
            Room room = existingMatch.getRoom();
            boolean shouldCreateOffer = partnerRequest != null && room != null
                    && shouldCreateOffer(me, partnerRequest.getUser());

            return buildMatchStartResponse(
                    MatchStartResponseDto.MatchState.MATCHED,
                    existingMatch,
                    partnerRequest,
                    room,
                    "이미 매칭된 상대가 있습니다.",
                    shouldCreateOffer
            );
        }

        MatchRequest waitingRequest = findLatestByStatuses(me, List.of(MatchRequest.MatchStatus.WAITING));
        if (waitingRequest != null) {
            long waitingCount = matchRequestRepository.countByStatus(MatchRequest.MatchStatus.WAITING);
            return MatchStartResponseDto.builder()
                    .state(MatchStartResponseDto.MatchState.ALREADY_WAITING)
                    .myRequestId(waitingRequest.getRequestId())
                    .waitingCount(waitingCount)
                    .message("이미 매칭 대기 중입니다.")
                    .build();
        }

        Optional<MatchRequest> opponentOptional = findCompatibleWaitingRequest(me, requestDto);
        if (opponentOptional.isPresent()) {
            MatchRequest opponentRequest = opponentOptional.get();
            User partner = opponentRequest.getUser();

            opponentRequest.setStatus(MatchRequest.MatchStatus.MATCHED);

            MatchRequest myRequest = MatchRequest.builder()
                    .user(me)
                    .choiceGender(MatchRequest.Gender.valueOf(requestDto.getChoiceGender()))
                    .minAge(requestDto.getMinAge())
                    .maxAge(requestDto.getMaxAge())
                    .regionCode(requestDto.getRegionCode())
                    .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                    .status(MatchRequest.MatchStatus.MATCHED)
                    .build();
            matchRequestRepository.save(myRequest);

            Room room = chatService.createPrivateRoom(me, partner);
            myRequest.setRoom(room);
            opponentRequest.setRoom(room);

            matchRequestRepository.save(opponentRequest);
            matchRequestRepository.save(myRequest);

            boolean myOffer = shouldCreateOffer(me, partner);
            boolean partnerOffer = shouldCreateOffer(partner, me);

            String message = "새로운 상대와 매칭되었습니다.";

            sendMatchEvent(me, myRequest, opponentRequest, room, MatchEventMessage.EventType.MATCH_FOUND, message, myOffer);
            sendMatchEvent(partner, opponentRequest, myRequest, room, MatchEventMessage.EventType.MATCH_FOUND, message, partnerOffer);

            return buildMatchStartResponse(
                    MatchStartResponseDto.MatchState.MATCHED,
                    myRequest,
                    opponentRequest,
                    room,
                    message,
                    myOffer
            );
        }

        MatchRequest newWaitingRequest = MatchRequest.builder()
                .user(me)
                .choiceGender(MatchRequest.Gender.valueOf(requestDto.getChoiceGender()))
                .minAge(requestDto.getMinAge())
                .maxAge(requestDto.getMaxAge())
                .regionCode(requestDto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                .status(MatchRequest.MatchStatus.WAITING)
                .build();
        matchRequestRepository.save(newWaitingRequest);

        long waitingCount = matchRequestRepository.countByStatus(MatchRequest.MatchStatus.WAITING);
        return MatchStartResponseDto.builder()
                .state(MatchStartResponseDto.MatchState.WAITING)
                .myRequestId(newWaitingRequest.getRequestId())
                .waitingCount(waitingCount)
                .message("매칭 대기열에 등록되었습니다.")
                .shouldCreateOffer(false)
                .build();
    }

    public MatchDecisionResponseDto acceptMatch(String loginId, Long requestId) {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MatchRequest myRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        validateOwnership(me, myRequest);
        if (myRequest.getStatus() != MatchRequest.MatchStatus.MATCHED
                && myRequest.getStatus() != MatchRequest.MatchStatus.CONFIRMED) {
            throw new IllegalArgumentException("현재 상태에서는 매칭을 수락할 수 없습니다.");
        }

        myRequest.setStatus(MatchRequest.MatchStatus.CONFIRMED);
        matchRequestRepository.save(myRequest);

        MatchRequest partnerRequest = findPartnerRequest(myRequest)
                .orElseThrow(() -> new IllegalArgumentException("상대방 매칭 정보를 찾을 수 없습니다."));

        boolean bothAccepted = partnerRequest.getStatus() == MatchRequest.MatchStatus.CONFIRMED;
        Room room = myRequest.getRoom();

        sendMatchEvent(
                partnerRequest.getUser(),
                partnerRequest,
                myRequest,
                room,
                MatchEventMessage.EventType.PARTNER_ACCEPTED,
                "상대방이 매칭을 수락했습니다.",
                shouldCreateOffer(partnerRequest.getUser(), me)
        );

        if (bothAccepted) {
            String confirmedMessage = "매칭이 확정되었습니다.";
            sendMatchEvent(me, myRequest, partnerRequest, room, MatchEventMessage.EventType.BOTH_CONFIRMED, confirmedMessage, shouldCreateOffer(me, partnerRequest.getUser()));
            sendMatchEvent(partnerRequest.getUser(), partnerRequest, myRequest, room, MatchEventMessage.EventType.BOTH_CONFIRMED, confirmedMessage, shouldCreateOffer(partnerRequest.getUser(), me));
        }

        return MatchDecisionResponseDto.builder()
                .decision(MatchDecisionResponseDto.Decision.ACCEPTED)
                .roomId(room != null ? room.getRoomId() : null)
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(partnerRequest.getRequestId())
                .myStatus(myRequest.getStatus())
                .partnerStatus(partnerRequest.getStatus())
                .bothAccepted(bothAccepted)
                .message(bothAccepted ? "상대와 연결이 확정되었습니다." : "매칭을 수락했습니다. 상대 응답을 기다리는 중입니다.")
                .build();
    }

    public MatchDecisionResponseDto declineMatch(String loginId, Long requestId) {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MatchRequest myRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        validateOwnership(me, myRequest);
        if (myRequest.getStatus() == MatchRequest.MatchStatus.DECLINED
                || myRequest.getStatus() == MatchRequest.MatchStatus.CANCELLED) {
            return buildDeclineResponse(myRequest, null, "이미 종료된 매칭입니다.");
        }
        if (myRequest.getStatus() == MatchRequest.MatchStatus.WAITING) {
            matchRequestRepository.delete(myRequest);
            return MatchDecisionResponseDto.builder()
                    .decision(MatchDecisionResponseDto.Decision.DECLINED)
                    .message("대기열에서 제외되었습니다.")
                    .myRequestId(requestId)
                    .build();
        }

        myRequest.setStatus(MatchRequest.MatchStatus.DECLINED);
        matchRequestRepository.save(myRequest);

        MatchRequest partnerRequest = findPartnerRequest(myRequest).orElse(null);
        Room room = myRequest.getRoom();

        if (partnerRequest != null) {
            partnerRequest.setStatus(MatchRequest.MatchStatus.CANCELLED);
            matchRequestRepository.save(partnerRequest);

            sendMatchEvent(
                    partnerRequest.getUser(),
                    partnerRequest,
                    myRequest,
                    room,
                    MatchEventMessage.EventType.PARTNER_DECLINED,
                    "상대방이 매칭을 거절했습니다.",
                    shouldCreateOffer(partnerRequest.getUser(), me)
            );
            sendMatchEvent(
                    partnerRequest.getUser(),
                    partnerRequest,
                    myRequest,
                    room,
                    MatchEventMessage.EventType.MATCH_CANCELLED,
                    "매칭이 취소되었습니다.",
                    false
            );
        }

        sendMatchEvent(me, myRequest, partnerRequest, room, MatchEventMessage.EventType.MATCH_CANCELLED, "매칭을 종료했습니다.", false);

        return buildDeclineResponse(myRequest, partnerRequest, "매칭을 거절했습니다.");
    }

    private MatchDecisionResponseDto buildDeclineResponse(MatchRequest myRequest, MatchRequest partnerRequest, String message) {
        return MatchDecisionResponseDto.builder()
                .decision(MatchDecisionResponseDto.Decision.DECLINED)
                .roomId(myRequest.getRoom() != null ? myRequest.getRoom().getRoomId() : null)
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(partnerRequest != null ? partnerRequest.getRequestId() : null)
                .myStatus(myRequest.getStatus())
                .partnerStatus(partnerRequest != null ? partnerRequest.getStatus() : MatchRequest.MatchStatus.CANCELLED)
                .bothAccepted(false)
                .message(message)
                .build();
    }

    private MatchStartResponseDto buildMatchStartResponse(
            MatchStartResponseDto.MatchState state,
            MatchRequest myRequest,
            MatchRequest partnerRequest,
            Room room,
            String message,
            boolean shouldCreateOffer
    ) {
        return MatchStartResponseDto.builder()
                .state(state)
                .myRequestId(myRequest != null ? myRequest.getRequestId() : null)
                .partnerRequestId(partnerRequest != null ? partnerRequest.getRequestId() : null)
                .roomId(room != null ? room.getRoomId() : null)
                .partnerLoginId(partnerRequest != null ? partnerRequest.getUser().getLoginId() : null)
                .partnerNickName(partnerRequest != null ? partnerRequest.getUser().getNickName() : null)
                .message(message)
                .shouldCreateOffer(shouldCreateOffer)
                .build();
    }

    private MatchRequest findLatestByStatuses(User user, List<MatchRequest.MatchStatus> statuses) {
        for (MatchRequest.MatchStatus status : statuses) {
            Optional<MatchRequest> request = matchRequestRepository.findTopByUserAndStatusOrderByRequestedAtDesc(user, status);
            if (request.isPresent()) {
                return request.get();
            }
        }
        return null;
    }

    private Optional<MatchRequest> findPartnerRequest(MatchRequest request) {
        Room room = request.getRoom();
        if (room == null) {
            return Optional.empty();
        }
        return matchRequestRepository.findFirstByRoomAndUserNot(room, request.getUser());
    }

    private Optional<MatchRequest> findCompatibleWaitingRequest(User me, MatchRequestDto requestDto) {
        List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                MatchRequest.MatchStatus.WAITING
        );

        long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());

        for (MatchRequest opponentRequest : potentialMatches) {
            User opponent = opponentRequest.getUser();
            long opponentAge = ChronoUnit.YEARS.between(opponent.getBirthDate(), LocalDate.now());

            boolean meetsOpponentGender = opponentRequest.getChoiceGender() == MatchRequest.Gender.A
                    || opponentRequest.getChoiceGender().name().equalsIgnoreCase(String.valueOf(me.getGender()));
            boolean meetsOpponentAge = myAge >= opponentRequest.getMinAge() && myAge <= opponentRequest.getMaxAge();

            boolean meetsMyGender = "A".equalsIgnoreCase(requestDto.getChoiceGender())
                    || requestDto.getChoiceGender().equalsIgnoreCase(String.valueOf(opponent.getGender()));
            boolean meetsMyAge = opponentAge >= requestDto.getMinAge() && opponentAge <= requestDto.getMaxAge();
            boolean meetsRegion = requestDto.getRegionCode().equalsIgnoreCase(opponentRequest.getRegionCode());

            if (meetsOpponentGender && meetsOpponentAge && meetsMyGender && meetsMyAge && meetsRegion) {
                return Optional.of(opponentRequest);
            }
        }

        return Optional.empty();
    }

    private void sendMatchEvent(
            User recipient,
            MatchRequest recipientRequest,
            MatchRequest partnerRequest,
            Room room,
            MatchEventMessage.EventType eventType,
            String message,
            boolean shouldCreateOffer
    ) {
        MatchEventMessage payload = MatchEventMessage.builder()
                .eventType(eventType)
                .roomId(room != null ? room.getRoomId() : null)
                .myRequestId(recipientRequest != null ? recipientRequest.getRequestId() : null)
                .partnerRequestId(partnerRequest != null ? partnerRequest.getRequestId() : null)
                .partnerLoginId(partnerRequest != null ? partnerRequest.getUser().getLoginId() : null)
                .partnerNickName(partnerRequest != null ? partnerRequest.getUser().getNickName() : null)
                .shouldCreateOffer(shouldCreateOffer)
                .message(message)
                .build();

        messagingTemplate.convertAndSendToUser(recipient.getLoginId(), "/queue/match-results", payload);
    }

    private void validateOwnership(User me, MatchRequest myRequest) {
        if (!myRequest.getUser().getUserPid().equals(me.getUserPid())) {
            throw new IllegalArgumentException("본인의 매칭 요청만 처리할 수 있습니다.");
        }
    }

    private boolean shouldCreateOffer(User current, User partner) {
        return current.getLoginId().compareToIgnoreCase(partner.getLoginId()) <= 0;
    }
}
