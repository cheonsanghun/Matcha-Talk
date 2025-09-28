package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchFoundResponseDto;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestHeader(value = "X-Login-Id", required = false) String headerLoginId,
            @Valid @RequestBody MatchRequestDto dto) {

        try {
            String loginId = resolveLoginId(userDetails, dto.getLoginId(), headerLoginId);
            if (!StringUtils.hasText(loginId)) {
                return ResponseEntity.badRequest().build();
            }

            log.info("Match request received from user: {}", loginId);
            log.info("Match request data: {}", dto);
            
            MatchStartResponseDto result = matchService.startOrFindMatch(loginId, dto);

            return ResponseEntity.ok(result);

        } catch (JsonProcessingException e) {
            log.error("JSON processing error during match request.", e);
            return ResponseEntity.internalServerError().build();
        } catch (IllegalStateException e) {
            log.warn("Match request could not be processed. Reason: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid match request. Reason: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/results/latest")
    public ResponseEntity<MatchFoundResponseDto> getLatestMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Login-Id", required = false) String headerLoginId) {

        try {
            String loginId = resolveLoginId(userDetails, null, headerLoginId);
            if (!StringUtils.hasText(loginId)) {
                return ResponseEntity.badRequest().build();
            }

            return matchService.findLatestMatch(loginId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid match lookup request. Reason: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<MatchFoundResponseDto> acceptMatchRequest(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Login-Id", required = false) String headerLoginId) {

        try {
            String loginId = resolveLoginId(userDetails, null, headerLoginId);
            if (!StringUtils.hasText(loginId)) {
                return ResponseEntity.badRequest().build();
            }

            MatchFoundResponseDto response = matchService.acceptMatchRequest(loginId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid accept request. Reason: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.warn("Accept request rejected. Reason: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<MatchFoundResponseDto> declineMatchRequest(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Login-Id", required = false) String headerLoginId) {
        try {
            String loginId = resolveLoginId(userDetails, null, headerLoginId);
            if (!StringUtils.hasText(loginId)) {
                return ResponseEntity.badRequest().build();
            }

            MatchFoundResponseDto response = matchService.declineMatchRequest(loginId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid decline request. Reason: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private String resolveLoginId(UserDetails principal, String dtoLoginId, String headerLoginId) {
        if (principal != null && StringUtils.hasText(principal.getUsername())) {
            return principal.getUsername();
        }
        if (StringUtils.hasText(dtoLoginId)) {
            return dtoLoginId.trim();
        }
        if (StringUtils.hasText(headerLoginId)) {
            return headerLoginId.trim();
        }
        return null;
    }
}
