package net.datasa.project01.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchFoundResponseDto;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.websocket.RealTimeMessagingService;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MatchService {

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final FollowRepository followRepository;
    private final RealTimeMessagingService messagingService;
    private final ObjectMapper objectMapper;

    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 랜덤 매칭을 시작하거나 대기열에서 상대를 찾기
     * @param loginId 요청한 사용자의 ID
     * @param requestDto 매칭 조건
     */
    public MatchStartResponseDto startOrFindMatch(String loginId, MatchRequestDto requestDto) throws JsonProcessingException {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Optional<MatchRequest> activeHandshake = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.MATCHED);
        if (activeHandshake.isPresent()) {
            MatchRequest handshakeRequest = activeHandshake.get();
            if (isHandshakeExpired(handshakeRequest)) {
                expireHandshake(handshakeRequest);
            } else {
                MatchRequest opponent = findHandshakePartner(handshakeRequest)
                        .orElseThrow(() -> new IllegalStateException("상대 매칭 정보를 찾을 수 없습니다."));
                MatchFoundResponseDto responseDto = buildMatchFoundResponse(handshakeRequest, opponent);
                return MatchStartResponseDto.matched(handshakeRequest, responseDto);
            }
        }

        Optional<MatchRequest> waitingRequest = matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING);
        if (waitingRequest.isPresent()) {
            log.info("User {} is already in the matching queue.", loginId);
            sendWaitingNotification(loginId);
            return MatchStartResponseDto.queued(waitingRequest.get(), true);
        }

        List<MatchRequest> potentialMatches = matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                MatchRequest.MatchStatus.WAITING,
                requestDto.getRegionCode()
        );

        MatchRequest matchedOpponentRequest = null;
        for (MatchRequest opponentRequest : potentialMatches) {
            if (isMutuallyCompatible(me, requestDto, opponentRequest)) {
                matchedOpponentRequest = opponentRequest;
                break;
            }
        }

        if (matchedOpponentRequest != null) {
            log.info("✅ Match found for user {}: {}", loginId, matchedOpponentRequest.getUser().getLoginId());

            String handshakeKey = UUID.randomUUID().toString();
            LocalDateTime expiresAt = LocalDateTime.now().plus(HANDSHAKE_TIMEOUT);

            matchedOpponentRequest.setStatus(MatchRequest.MatchStatus.MATCHED);
            matchedOpponentRequest.setHandshakeKey(handshakeKey);
            matchedOpponentRequest.setHandshakeExpiresAt(expiresAt);

            MatchRequest myMatchedRequest = buildMatchRequest(me, requestDto, MatchRequest.MatchStatus.MATCHED);
            myMatchedRequest.setHandshakeKey(handshakeKey);
            myMatchedRequest.setHandshakeExpiresAt(expiresAt);

            matchRequestRepository.save(myMatchedRequest);
            matchRequestRepository.save(matchedOpponentRequest);

            MatchFoundResponseDto myResponse = buildMatchFoundResponse(myMatchedRequest, matchedOpponentRequest);
            MatchFoundResponseDto opponentResponse = buildMatchFoundResponse(matchedOpponentRequest, myMatchedRequest);

            notifyMatchFound(myMatchedRequest, myResponse);
            notifyMatchFound(matchedOpponentRequest, opponentResponse);

            return MatchStartResponseDto.matched(myMatchedRequest, myResponse);
        }

        log.info("❌ No match found for user {}. Adding to queue.", loginId);
        MatchRequest newRequest = buildMatchRequest(me, requestDto, MatchRequest.MatchStatus.WAITING);
        matchRequestRepository.save(newRequest);
        sendWaitingNotification(loginId);
        return MatchStartResponseDto.queued(newRequest, false);
    }

    @Transactional(readOnly = true)
    public Optional<MatchFoundResponseDto> findLatestMatch(String loginId) {
        User me = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<MatchRequest.MatchStatus> candidateStatuses = List.of(
                MatchRequest.MatchStatus.CONFIRMED,
                MatchRequest.MatchStatus.MATCHED
        );

        return matchRequestRepository.findFirstByUserAndStatusInOrderByRequestedAtDesc(me, candidateStatuses)
                .flatMap(request -> {
                    if (request.getStatus() == MatchRequest.MatchStatus.MATCHED && isHandshakeExpired(request)) {
                        return Optional.empty();
                    }

                    Optional<MatchRequest> partner = request.getStatus() == MatchRequest.MatchStatus.CONFIRMED
                            ? findConfirmedPartner(request)
                            : findHandshakePartner(request);

                    return Optional.of(buildMatchFoundResponse(request, partner.orElse(null)));
                });
    }

    /**
     * ✅ 테스트용: 대기 상태 알림
     */
    private void sendWaitingNotification(String loginId) {
        try {
            log.info("📋 Sending waiting notification to user: {}", loginId);

            String waitingMessage = "매칭 대기 중입니다. 상대방을 찾고 있어요...";

            // 사용자별 큐로 전송
            messagingService.sendEventToUser(loginId, RealTimeMessagingService.EVENT_MATCH_STATUS, waitingMessage);

            log.info("✅ Waiting notification sent to: {}", loginId);
        } catch (Exception e) {
            log.error("❌ Failed to send waiting notification to user {}: {}", loginId, e.getMessage());
        }
    }

    public MatchFoundResponseDto acceptMatchRequest(String loginId, Long requestId) {
        MatchRequest request = matchRequestRepository.findByIdWithUser(requestId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        validateOwnership(loginId, request);

        if (request.getStatus() != MatchRequest.MatchStatus.MATCHED) {
            throw new IllegalStateException("이미 처리된 매칭입니다.");
        }

        if (isHandshakeExpired(request)) {
            expireHandshake(request);
            throw new IllegalStateException("매칭 수락 시간이 만료되었습니다.");
        }

        request.setStatus(MatchRequest.MatchStatus.CONFIRMED);
        matchRequestRepository.save(request);

        MatchRequest partner = findHandshakePartner(request)
                .orElseThrow(() -> new IllegalStateException("상대 매칭 정보를 찾을 수 없습니다."));

        if (partner.getStatus() == MatchRequest.MatchStatus.CONFIRMED) {
            Room room = chatService.createRandomMatchRoom(request.getUser(), partner.getUser());
            request.setRoom(room);
            partner.setRoom(room);
            request.setHandshakeExpiresAt(null);
            partner.setHandshakeExpiresAt(null);
            request.setHandshakeKey(null);
            partner.setHandshakeKey(null);

            matchRequestRepository.save(request);
            matchRequestRepository.save(partner);

            MatchFoundResponseDto myPayload = buildMatchFoundResponse(request, partner);
            MatchFoundResponseDto partnerPayload = buildMatchFoundResponse(partner, request);

            messagingService.sendEventToUser(loginId, RealTimeMessagingService.EVENT_MATCH_ROOM_READY, myPayload);
            messagingService.sendEventToUser(partner.getUser().getLoginId(), RealTimeMessagingService.EVENT_MATCH_ROOM_READY, partnerPayload);

            return myPayload;
        }

        MatchFoundResponseDto response = buildMatchFoundResponse(request, partner);
        messagingService.sendEventToUser(partner.getUser().getLoginId(), RealTimeMessagingService.EVENT_MATCH_FOUND, buildMatchFoundResponse(partner, request));
        return response;
    }

    public MatchFoundResponseDto declineMatchRequest(String loginId, Long requestId) {
        MatchRequest request = matchRequestRepository.findByIdWithUser(requestId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 요청을 찾을 수 없습니다."));

        validateOwnership(loginId, request);

        request.setStatus(MatchRequest.MatchStatus.DECLINED);
        request.setHandshakeExpiresAt(null);
        request.setHandshakeKey(null);
        matchRequestRepository.save(request);

        MatchRequest partner = findHandshakePartner(request)
                .map(other -> {
                    other.setStatus(MatchRequest.MatchStatus.DECLINED);
                    other.setHandshakeExpiresAt(null);
                    other.setHandshakeKey(null);
                    matchRequestRepository.save(other);
                    return other;
                })
                .orElse(null);

        MatchFoundResponseDto response = buildMatchFoundResponse(request, partner);

        if (partner != null) {
            MatchFoundResponseDto partnerResponse = buildMatchFoundResponse(partner, request);
            messagingService.sendEventToUser(partner.getUser().getLoginId(), RealTimeMessagingService.EVENT_MATCH_DECLINED, partnerResponse);
        }

        messagingService.sendEventToUser(loginId, RealTimeMessagingService.EVENT_MATCH_DECLINED, response);
        return response;
    }

    private MatchRequest buildMatchRequest(User user, MatchRequestDto requestDto, MatchRequest.MatchStatus status) throws JsonProcessingException {
        Integer minAge = requestDto.getMinAge();
        Integer maxAge = requestDto.getMaxAge();

        if (minAge != null && maxAge != null && minAge > maxAge) {
            int swappedMin = maxAge;
            maxAge = minAge;
            minAge = swappedMin;
        }

        return MatchRequest.builder()
                .user(user)
                .choiceGender(MatchRequest.Gender.valueOf(requestDto.getChoiceGender()))
                .minAge(minAge)
                .maxAge(maxAge)
                .regionCode(requestDto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(requestDto.getInterests()))
                .status(status)
                .build();
    }

    private MatchFoundResponseDto buildMatchFoundResponse(MatchRequest myRequest, MatchRequest partnerRequest) {
        Long partnerRequestId = partnerRequest != null ? partnerRequest.getRequestId() : null;
        User partnerUser = partnerRequest != null ? partnerRequest.getUser() : null;
        String partnerLoginId = partnerUser != null ? partnerUser.getLoginId() : null;
        String partnerNickName = partnerUser != null ? partnerUser.getNickName() : null;

        Room resolvedRoom = myRequest.getRoom() != null
                ? myRequest.getRoom()
                : (partnerRequest != null ? partnerRequest.getRoom() : null);
        Long roomId = resolvedRoom != null ? resolvedRoom.getRoomId() : null;
        Boolean roomTemporary = resolvedRoom != null ? resolvedRoom.isTemporary() : null;

        FollowSnapshot followSnapshot = resolveFollowSnapshot(myRequest.getUser(), partnerUser);

        return MatchFoundResponseDto.builder()
                .myRequestId(myRequest.getRequestId())
                .partnerRequestId(partnerRequestId)
                .partnerLoginId(partnerLoginId)
                .partnerNickName(partnerNickName)
                .partnerUserPid(partnerUser != null ? partnerUser.getUserPid() : null)
                .roomId(roomId)
                .handshakeKey(myRequest.getHandshakeKey())
                .expiresAt(myRequest.getHandshakeExpiresAt())
                .status(myRequest.getStatus())
                .followStatus(followSnapshot.displayStatus())
                .followRelationId(followSnapshot.relationId())
                .incomingFollowId(followSnapshot.incomingId())
                .incomingFollowStatus(followSnapshot.incomingStatus())
                .outgoingFollowId(followSnapshot.outgoingId())
                .outgoingFollowStatus(followSnapshot.outgoingStatus())
                .mutualFollow(followSnapshot.mutualAccepted())
                .roomTemporary(roomTemporary)
                .build();
    }

    public void archiveMatchRequestsForRoom(Room room) {
        List<MatchRequest> requests = matchRequestRepository.findAllByRoomAndStatusIn(
                room,
                java.util.List.of(MatchRequest.MatchStatus.CONFIRMED)
        );

        if (requests.isEmpty()) {
            return;
        }

        for (MatchRequest request : requests) {
            request.setStatus(MatchRequest.MatchStatus.ARCHIVED);
        }

        matchRequestRepository.saveAll(requests);
    }

    private FollowSnapshot resolveFollowSnapshot(User currentUser, User partnerUser) {
        if (currentUser == null || partnerUser == null) {
            return FollowSnapshot.empty();
        }

        Optional<Follow> outgoing = followRepository.findByFollowerAndFollowee(currentUser, partnerUser);
        Optional<Follow> incoming = followRepository.findByFollowerAndFollowee(partnerUser, currentUser);

        return FollowSnapshot.from(outgoing, incoming);
    }

    private record FollowSnapshot(Long outgoingId,
                                  String outgoingStatus,
                                  Long incomingId,
                                  String incomingStatus,
                                  String displayStatus,
                                  Long relationId,
                                  boolean mutualAccepted) {
        static FollowSnapshot empty() {
            return new FollowSnapshot(null, null, null, null, null, null, false);
        }

        static FollowSnapshot from(Optional<Follow> outgoing, Optional<Follow> incoming) {
            Follow.FollowStatus outgoingStatusEnum = outgoing.map(Follow::getStatus).orElse(null);
            Follow.FollowStatus incomingStatusEnum = incoming.map(Follow::getStatus).orElse(null);

            String outgoingStatus = outgoingStatusEnum != null ? outgoingStatusEnum.name() : null;
            String incomingStatus = incomingStatusEnum != null ? incomingStatusEnum.name() : null;

            boolean mutualAccepted = outgoingStatusEnum == Follow.FollowStatus.ACCEPTED
                    && incomingStatusEnum == Follow.FollowStatus.ACCEPTED;

            String displayStatus = null;
            Long relationId = null;

            if (incomingStatusEnum == Follow.FollowStatus.PENDING) {
                displayStatus = "PENDING_INCOMING";
                relationId = incoming.map(Follow::getFollowId).orElse(null);
            } else if (outgoingStatusEnum == Follow.FollowStatus.PENDING) {
                displayStatus = "PENDING_OUTGOING";
                relationId = outgoing.map(Follow::getFollowId).orElse(null);
            } else if (mutualAccepted) {
                displayStatus = "ACCEPTED";
                relationId = outgoing.map(Follow::getFollowId)
                        .orElseGet(() -> incoming.map(Follow::getFollowId).orElse(null));
            } else if (incomingStatusEnum == Follow.FollowStatus.ACCEPTED) {
                displayStatus = "ACCEPTED_INCOMING";
                relationId = incoming.map(Follow::getFollowId).orElse(null);
            } else if (outgoingStatusEnum == Follow.FollowStatus.ACCEPTED) {
                displayStatus = "ACCEPTED_OUTGOING";
                relationId = outgoing.map(Follow::getFollowId).orElse(null);
            } else if (incomingStatusEnum == Follow.FollowStatus.REJECTED) {
                displayStatus = "REJECTED_INCOMING";
            } else if (outgoingStatusEnum == Follow.FollowStatus.REJECTED) {
                displayStatus = "REJECTED_OUTGOING";
            }

            return new FollowSnapshot(
                    outgoing.map(Follow::getFollowId).orElse(null),
                    outgoingStatus,
                    incoming.map(Follow::getFollowId).orElse(null),
                    incomingStatus,
                    displayStatus,
                    relationId,
                    mutualAccepted
            );
        }
    }

    private Optional<MatchRequest> findHandshakePartner(MatchRequest request) {
        String handshakeKey = request.getHandshakeKey();
        if (handshakeKey == null) {
            return Optional.empty();
        }

        return matchRequestRepository.findAllByHandshakeKey(handshakeKey).stream()
                .filter(other -> !other.getRequestId().equals(request.getRequestId()))
                .findFirst();
    }

    private Optional<MatchRequest> findConfirmedPartner(MatchRequest request) {
        Room room = request.getRoom();
        if (room == null) {
            return Optional.empty();
        }
        return matchRequestRepository.findByRoom(room).stream()
                .filter(other -> !other.getRequestId().equals(request.getRequestId()))
                .findFirst();
    }

    private boolean isHandshakeExpired(MatchRequest request) {
        LocalDateTime expiresAt = request.getHandshakeExpiresAt();
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    private void expireHandshake(MatchRequest request) {
        findHandshakePartner(request).ifPresent(partner -> {
            partner.setStatus(MatchRequest.MatchStatus.CANCELLED);
            partner.setHandshakeKey(null);
            partner.setHandshakeExpiresAt(null);
            matchRequestRepository.save(partner);
        });

        request.setStatus(MatchRequest.MatchStatus.CANCELLED);
        request.setHandshakeKey(null);
        request.setHandshakeExpiresAt(null);
        matchRequestRepository.save(request);
    }

    private void notifyMatchFound(MatchRequest request, MatchFoundResponseDto payload) {
        try {
            messagingService.sendEventToUser(request.getUser().getLoginId(), RealTimeMessagingService.EVENT_MATCH_FOUND, payload);
        } catch (Exception e) {
            log.error("❌ Failed to send match found event to user {}: {}", request.getUser().getLoginId(), e.getMessage());
        }
    }

    private void validateOwnership(String loginId, MatchRequest request) {
        if (!request.getUser().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("본인의 매칭 요청만 처리할 수 있습니다.");
        }
    }

    private boolean isMutuallyCompatible(User me, MatchRequestDto myRequestDto, MatchRequest opponentRequest) {
        User opponent = opponentRequest.getUser();
        long myAge = ChronoUnit.YEARS.between(me.getBirthDate(), LocalDate.now());
        long opponentAge = ChronoUnit.YEARS.between(opponent.getBirthDate(), LocalDate.now());

        if (!isGenderSatisfied(opponentRequest.getChoiceGender(), me.getGender())) {
            return false;
        }
        if (!isGenderSatisfied(MatchRequest.Gender.valueOf(myRequestDto.getChoiceGender()), opponent.getGender())) {
            return false;
        }

        if (!isAgeWithinBounds(myAge, opponentRequest.getMinAge(), opponentRequest.getMaxAge())) {
            return false;
        }
        if (!isAgeWithinBounds(opponentAge, myRequestDto.getMinAge(), myRequestDto.getMaxAge())) {
            return false;
        }

        if (!hasInterestOverlap(myRequestDto.getInterests(), opponentRequest.getInterestsJson())) {
            return false;
        }

        String requestedRegion = myRequestDto.getRegionCode();
        if (requestedRegion != null && !requestedRegion.equals(opponentRequest.getRegionCode())) {
            return false;
        }

        return true;
    }

    private boolean isGenderSatisfied(MatchRequest.Gender desiredGender, Character targetGender) {
        if (desiredGender == MatchRequest.Gender.A) {
            return true;
        }
        return targetGender != null && desiredGender.name().equalsIgnoreCase(targetGender.toString());
    }

    private boolean isAgeWithinBounds(long age, Integer minAge, Integer maxAge) {
        if (minAge != null && age < minAge) {
            return false;
        }
        if (maxAge != null && age > maxAge) {
            return false;
        }
        return true;
    }

    private boolean hasInterestOverlap(List<String> myInterests, String opponentInterestsJson) {
        if (myInterests == null || myInterests.isEmpty()) {
            return false;
        }
        try {
            List<String> opponentInterests = objectMapper.readValue(opponentInterestsJson, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            return opponentInterests.stream()
                    .map(String::toLowerCase)
                    .anyMatch(opponentInterest -> myInterests.stream()
                            .map(String::toLowerCase)
                            .anyMatch(myInterest -> myInterest.equals(opponentInterest)));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse opponent interests JSON: {}", opponentInterestsJson, e);
            return false;
        }
    }
}