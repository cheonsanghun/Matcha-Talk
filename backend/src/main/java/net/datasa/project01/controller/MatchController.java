package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchDecisionResponseDto;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@Slf4j
public class MatchController {

    private final MatchService matchService;
    private final MatchRequestRepository matchRequestRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    /**
     * 매칭 요청(단일 엔드포인트)
     * - 인증 정보(@AuthenticationPrincipal)가 있으면 매칭 로직 실행
     * - 인증 정보가 없고 X-USER-PID 헤더가 있으면 DB에 요청 레코드 저장
     * - 둘 다 없으면 400
     */
    @PostMapping("/requests")
    public ResponseEntity<?> createOrStartMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-USER-PID", required = false) Long userPid,
            @Valid @RequestBody MatchRequestDto dto) {

        try {
            // 1) Spring Security 인증 기반 흐름
            if (userDetails != null) {
                String loginId = userDetails.getUsername();
                log.info("Match request received from authenticated user: {}", loginId);

                // 서비스 레이어에 매칭 시작/탐색 위임
                MatchStartResponseDto response = matchService.startOrFindMatch(loginId, dto);
                return ResponseEntity.ok(response);
            }

            // 2) 헤더 기반(비인증) 흐름: 요청 엔티티 저장 후 requestId 반환
            if (userPid != null) {
                User user = userRepository.findById(userPid)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

                if (dto.getInterests() == null || dto.getInterests().isEmpty()) {
                    throw new IllegalArgumentException("관심사는 최소 1개 이상 선택해야 합니다.");
                }
                MatchRequest saved = matchRequestRepository.save(
                        MatchRequest.builder()
                                .user(user)
                                .choiceGender(MatchRequest.Gender.valueOf(dto.getChoiceGender()))
                                .minAge(dto.getMinAge())
                                .maxAge(dto.getMaxAge())
                                .regionCode(dto.getRegionCode())
                                .interestsJson(objectMapper.writeValueAsString(dto.getInterests()))
                                .build()
                );
                return ResponseEntity.ok(Map.of("requestId", saved.getRequestId()));
            }

            // 3) 둘 다 없는 경우
            return ResponseEntity.badRequest()
                    .body("인증 정보가 없습니다. 로그인(@AuthenticationPrincipal) 또는 X-USER-PID 헤더 중 하나가 필요합니다.");

        } catch (JsonProcessingException e) {
            log.error("JSON processing error during match request. user={} / userPid={}",
                    (userDetails != null ? userDetails.getUsername() : "null"), userPid, e);
            return ResponseEntity.internalServerError().body("매칭 요청 처리 중 오류가 발생했습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid match request. user={} / userPid={} / reason={}",
                    (userDetails != null ? userDetails.getUsername() : "null"), userPid, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<?> acceptMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("인증 정보가 필요합니다.");
        }
        try {
            MatchDecisionResponseDto response = matchService.respondToMatch(userDetails.getUsername(), requestId, true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Accept failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<?> declineMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("인증 정보가 필요합니다.");
        }
        try {
            MatchDecisionResponseDto response = matchService.respondToMatch(userDetails.getUsername(), requestId, false);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Decline failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
