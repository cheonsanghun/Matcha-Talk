package net.datasa.project01.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@Slf4j
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/requests")
    public ResponseEntity<?> startMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MatchRequestDto dto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("인증 정보가 필요합니다.");
        }
        try {
            MatchStartResponseDto response = matchService.startOrFindMatch(userDetails.getUsername(), dto);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid match request from {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<?> getMatchStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("인증 정보가 필요합니다.");
        }
        try {
            MatchStartResponseDto response = matchService.getMatchStatus(userDetails.getUsername(), requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Match status lookup failed for user {} and request {}: {}",
                    userDetails.getUsername(), requestId, e.getMessage());
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
