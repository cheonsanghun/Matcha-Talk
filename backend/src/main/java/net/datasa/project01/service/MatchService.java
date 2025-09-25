package net.datasa.project01.service;

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

import java.util.List;
import java.util.Locale;
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

    public MatchStartResponseDto startOrFindMatch(String loginId, MatchRequestDto requestDto) {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MatchRequest existingWaiting = matchRequestRepository
                .findFirstByUserAndStatusOrderByRequestedAtDesc(me, MatchRequest.MatchStatus.WAITING)
                .orElse(null);
        if (existingWaiting != null) {
            log.debug("User {} is already waiting for a match", loginId);
            return buildWaitingResponse(existingWaiting, true);
        }

        MatchRequest existingMatched = findActiveMatch(me);
        if (existingMatched != null && existingMatched.getRoom() != null) {
            MatchRequest partner = findPartner(existingMatched);
            return buildMatchedResponse(existingMatched, partner, "이미 진행 중인 매칭이 있습니다.");
        }

        Optional<MatchRequest> opponentOptional = matchRequestRepository
                .findFirstByStatusAndUserNotOrderByRequestedAtAsc(MatchRequest.MatchStatus.WAITING, me);

        if (opponentOptional.isPresent()) {
            MatchRequest opponent = opponentOptional.get();
            User opponentUser = opponent.getUser();
            Room room = chatService.createPrivateRoom(me, opponentUser);

            opponent.setStatus(MatchRequest.MatchStatus.MATCHED);
            opponent.setRoom(room);

            MatchRequest myRequest = matchRequestRepository.save(MatchRequest.builder()
                    .user(me)
                    .choiceGender(parseChoiceGender(requestDto))
                    .minAge(requestDto.getMinAge())
                    .maxAge(requestDto.getMaxAge())
                    .regionCode(requestDto.getRegionCode())
                    .interestsJson(toJsonArray(requestDto.getInterests()))
                    .status(MatchRequest.MatchStatus.MATCHED)
                    .room(room)
                    .build());

            MatchEventMessage myEvent = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.MATCH_FOUND)
                    .roomId(room.getRoomId())
                    .myRequestId(myRequest.getRequestId())
                    .partnerRequestId(opponent.getRequestId())
                    .partnerLoginId(opponentUser.getLoginId())
                    .partnerNickName(opponentUser.getNickName())
                    .message(String.format("%s님과 매칭되었습니다.", opponentUser.getNickName()))
                    .shouldCreateOffer(shouldCreateOffer(myRequest, opponent))
                    .build();
            sendMatchEvent(me, myEvent);

            MatchEventMessage partnerEvent = MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.MATCH_FOUND)
                    .roomId(room.getRoomId())
                    .myRequestId(opponent.getRequestId())
                    .partnerRequestId(myRequest.getRequestId())
                    .partnerLoginId(me.getLoginId())
                    .partnerNickName(me.getNickName())
                    .message(String.format("%s님과 매칭되었습니다.", me.getNickName()))
                    .shouldCreateOffer(shouldCreateOffer(opponent, myRequest))
                    .build();
            sendMatchEvent(opponentUser, partnerEvent);

            log.info("Match created between {} and {} in room {}", loginId, opponentUser.getLoginId(), room.getRoomId());
            return buildMatchedResponse(myRequest, opponent, "매칭이 성사되었습니다.");
        }

        MatchRequest waiting = matchRequestRepository.save(MatchRequest.builder()
                .user(me)
                .choiceGender(parseChoiceGender(requestDto))
                .minAge(requestDto.getMinAge())
                .maxAge(requestDto.getMaxAge())
                .regionCode(requestDto.getRegionCode())
                .interestsJson(toJsonArray(requestDto.getInterests()))
                .status(MatchRequest.MatchStatus.WAITING)
                .build());

        log.info("User {} entered the waiting queue", loginId);
        return buildWaitingResponse(waiting, false);
    }

    @Transactional(readOnly = true)
    public MatchStartResponseDto getMatchStatus(String loginId, Long requestId) {
        MatchRequest myRequest = matchRequestRepository.findByRequestIdAndUser_LoginId(requestId, loginId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        if (myRequest.getStatus() == MatchRequest.MatchStatus.WAITING) {
            return buildWaitingResponse(myRequest, true);
        }

        MatchRequest partner = findPartner(myRequest);
        return buildMatchedResponse(myRequest, partner, "매칭이 성사되었습니다.");
    }

    public MatchDecisionResponseDto respondToMatch(String loginId, Long requestId, boolean accept) {
        MatchRequest myRequest = matchRequestRepository.findByRequestIdAndUser_LoginId(requestId, loginId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        if (myRequest.getRoom() == null) {
            throw new IllegalStateException("매칭 세션 정보가 없습니다.");
        }

        if (myRequest.getStatus() != MatchRequest.MatchStatus.MATCHED
                && myRequest.getStatus() != MatchRequest.MatchStatus.CONFIRMED) {
            throw new IllegalStateException("현재 상태에서는 응답할 수 없습니다.");
        }

        MatchRequest partner = findPartner(myRequest);
        boolean partnerConfirmed = partner != null && partner.getStatus() == MatchRequest.MatchStatus.CONFIRMED;
        Long roomId = myRequest.getRoom().getRoomId();

        if (accept) {
            myRequest.setStatus(MatchRequest.MatchStatus.CONFIRMED);
            if (partner != null) {
                if (partnerConfirmed) {
                    notifyBothConfirmed(myRequest, partner);
                } else {
                    sendMatchEvent(partner.getUser(), MatchEventMessage.builder()
                            .eventType(MatchEventMessage.EventType.PARTNER_ACCEPTED)
                            .roomId(myRequest.getRoom().getRoomId())
                            .myRequestId(partner.getRequestId())
                            .partnerRequestId(myRequest.getRequestId())
                            .partnerLoginId(loginId)
                            .partnerNickName(myRequest.getUser().getNickName())
                            .message(String.format("%s님이 매칭을 수락했습니다.", myRequest.getUser().getNickName()))
                            .shouldCreateOffer(false)
                            .build());
                }
            }

            String message = partnerConfirmed
                    ? "상대도 이미 수락했습니다. 지금 바로 대화를 시작하세요."
                    : "매칭을 수락했습니다. 상대의 응답을 기다리는 중입니다.";

            return MatchDecisionResponseDto.builder()
                    .decision(MatchDecisionResponseDto.Decision.ACCEPTED)
                    .roomId(roomId)
                    .myRequestId(myRequest.getRequestId())
                    .partnerRequestId(partner != null ? partner.getRequestId() : null)
                    .myStatus(myRequest.getStatus())
                    .partnerStatus(partner != null ? partner.getStatus() : null)
                    .bothAccepted(partnerConfirmed)
                    .message(message)
                    .build();
        }

        myRequest.setStatus(MatchRequest.MatchStatus.DECLINED);
        myRequest.setRoom(null);

        if (partner != null) {
            Long partnerRoomId = roomId;
            partner.setStatus(MatchRequest.MatchStatus.CANCELLED);
            partner.setRoom(null);
            sendMatchEvent(partner.getUser(), MatchEventMessage.builder()
                    .eventType(MatchEventMessage.EventType.PARTNER_DECLINED)
                    .roomId(partnerRoomId)
                    .myRequestId(partner.getRequestId())
                    .partnerRequestId(myRequest.getRequestId())
                    .partnerLoginId(loginId)
                    .partnerNickName(myRequest.getUser().getNickName())
                    .message(String.format("%s님이 매칭을 거절했습니다.", myRequest.getUser().getNickName()))
                    .shouldCreateOffer(false)
                    .build());
        }

        return MatchDecisionResponseDto.builder()
                .decision(MatchDecisionResponseDto.Decision.DECLINED)
                .roomId(roomId)
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(partner != null ? partner.getRequestId() : null)
                .myStatus(myRequest.getStatus())
                .partnerStatus(partner != null ? partner.getStatus() : null)
                .bothAccepted(false)
                .message("매칭을 거절했습니다.")
                .build();
    }

    private MatchRequest findActiveMatch(User user) {
        MatchRequest matched = matchRequestRepository
                .findFirstByUserAndStatusOrderByRequestedAtDesc(user, MatchRequest.MatchStatus.MATCHED)
                .orElse(null);
        if (matched != null) {
            return matched;
        }
        return matchRequestRepository
                .findFirstByUserAndStatusOrderByRequestedAtDesc(user, MatchRequest.MatchStatus.CONFIRMED)
                .orElse(null);
    }

    private MatchRequest findPartner(MatchRequest myRequest) {
        if (myRequest.getRoom() == null) {
            return null;
        }
        List<MatchRequest> participants = matchRequestRepository.findByRoom(myRequest.getRoom());
        return participants.stream()
                .filter(request -> !request.getRequestId().equals(myRequest.getRequestId()))
                .findFirst()
                .orElse(null);
    }

    private MatchStartResponseDto buildWaitingResponse(MatchRequest request, boolean alreadyWaiting) {
        long totalWaiting = matchRequestRepository.countByStatus(MatchRequest.MatchStatus.WAITING);
        if (totalWaiting > 0) {
            totalWaiting = Math.max(0, totalWaiting - 1);
        }
        String message = totalWaiting > 0 ? "다른 사용자를 찾고 있습니다." : "현재 대기 중인 사용자가 없습니다.";
        return MatchStartResponseDto.builder()
                .state(alreadyWaiting ? MatchStartResponseDto.MatchState.ALREADY_WAITING : MatchStartResponseDto.MatchState.WAITING)
                .myRequestId(request.getRequestId())
                .waitingCount(totalWaiting)
                .message(message)
                .shouldCreateOffer(false)
                .myStatus(request.getStatus())
                .partnerStatus(null)
                .bothAccepted(false)
                .build();
    }

    private MatchStartResponseDto buildMatchedResponse(MatchRequest myRequest, MatchRequest partner, String message) {
        boolean bothAccepted = partner != null
                && myRequest.getStatus() == MatchRequest.MatchStatus.CONFIRMED
                && partner.getStatus() == MatchRequest.MatchStatus.CONFIRMED;

        return MatchStartResponseDto.builder()
                .state(MatchStartResponseDto.MatchState.MATCHED)
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(partner != null ? partner.getRequestId() : null)
                .roomId(myRequest.getRoom() != null ? myRequest.getRoom().getRoomId() : null)
                .partnerLoginId(partner != null ? partner.getUser().getLoginId() : null)
                .partnerNickName(partner != null ? partner.getUser().getNickName() : null)
                .waitingCount(0)
                .message(message)
                .shouldCreateOffer(shouldCreateOffer(myRequest, partner))
                .myStatus(myRequest.getStatus())
                .partnerStatus(partner != null ? partner.getStatus() : null)
                .bothAccepted(bothAccepted)
                .build();
    }

    private void notifyBothConfirmed(MatchRequest requestA, MatchRequest requestB) {
        Room room = requestA.getRoom();
        if (room == null) {
            return;
        }

        MatchEventMessage first = MatchEventMessage.builder()
                .eventType(MatchEventMessage.EventType.BOTH_CONFIRMED)
                .roomId(room.getRoomId())
                .myRequestId(requestA.getRequestId())
                .partnerRequestId(requestB.getRequestId())
                .partnerLoginId(requestB.getUser().getLoginId())
                .partnerNickName(requestB.getUser().getNickName())
                .message("서로 매칭을 수락했습니다. 대화를 시작하세요!")
                .shouldCreateOffer(shouldCreateOffer(requestA, requestB))
                .build();
        sendMatchEvent(requestA.getUser(), first);

        MatchEventMessage second = MatchEventMessage.builder()
                .eventType(MatchEventMessage.EventType.BOTH_CONFIRMED)
                .roomId(room.getRoomId())
                .myRequestId(requestB.getRequestId())
                .partnerRequestId(requestA.getRequestId())
                .partnerLoginId(requestA.getUser().getLoginId())
                .partnerNickName(requestA.getUser().getNickName())
                .message("서로 매칭을 수락했습니다. 대화를 시작하세요!")
                .shouldCreateOffer(shouldCreateOffer(requestB, requestA))
                .build();
        sendMatchEvent(requestB.getUser(), second);
    }

    private void sendMatchEvent(User target, MatchEventMessage message) {
        if (target == null || message == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(target.getLoginId(), "/queue/match-results", message);
    }

    private MatchRequest.Gender parseChoiceGender(MatchRequestDto dto) {
        if (dto == null || dto.getChoiceGender() == null) {
            return MatchRequest.Gender.A;
        }
        return MatchRequest.Gender.valueOf(dto.getChoiceGender().toUpperCase(Locale.ROOT));
    }

    private boolean shouldCreateOffer(MatchRequest me, MatchRequest partner) {
        if (me == null || partner == null) {
            return false;
        }
        Long myId = me.getRequestId();
        Long partnerId = partner.getRequestId();
        if (myId == null || partnerId == null) {
            return false;
        }
        return myId > partnerId;
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(values.get(i))).append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }
}
