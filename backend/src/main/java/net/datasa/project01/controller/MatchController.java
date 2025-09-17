package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchDecisionResponseDto;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@Slf4j
public class MatchController {

    private final MatchService matchService;

    /**
     * 랜덤 매칭을 요청하는 API 엔드포인트
     * @param userDetails 현재 인증된 사용자의 정보 (Spring Security가 주입)
     * @param dto 프론트엔드에서 보낸 매칭 조건
     * @return 요청 접수 결과
     */
    @PostMapping("/requests")
    public ResponseEntity<MatchStartResponseDto> startRandomMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MatchRequestDto dto) {

        try {
            String loginId = userDetails.getUsername();
            log.info("Match request received from user: {}", loginId);
            MatchStartResponseDto response = matchService.startOrFindMatch(loginId, dto);

            return ResponseEntity.ok(response);

        } catch (JsonProcessingException e) {
            log.error("JSON processing error during match request for user: {}", userDetails.getUsername(), e);
            return ResponseEntity.internalServerError().body(
                    MatchStartResponseDto.builder()
                            .message("매칭 요청 처리 중 오류가 발생했습니다.")
                            .build()
            );
        }
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<MatchDecisionResponseDto> acceptMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        String loginId = userDetails.getUsername();
        log.info("Match accept request from user: {} for requestId {}", loginId, requestId);
        MatchDecisionResponseDto response = matchService.acceptMatch(loginId, requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<MatchDecisionResponseDto> declineMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        String loginId = userDetails.getUsername();
        log.info("Match decline request from user: {} for requestId {}", loginId, requestId);
        MatchDecisionResponseDto response = matchService.declineMatch(loginId, requestId);
        return ResponseEntity.ok(response);
    }
}
