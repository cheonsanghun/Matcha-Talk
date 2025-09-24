package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowRequestCreateDto;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.domain.dto.FollowSummaryDto;
import net.datasa.project01.service.FollowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
@Slf4j
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ResponseEntity<?> createFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FollowRequestCreateDto requestDto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증 정보가 필요합니다."));
        }
        try {
            FollowResponseDto response = followService.requestFollow(userDetails.getUsername(), requestDto);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Follow request failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<?> getFollowStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증 정보가 필요합니다."));
        }
        try {
            FollowResponseDto response = followService.getFollowStatus(userDetails.getUsername(), roomId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Follow status lookup failed for user {} and room {}: {}", userDetails.getUsername(), roomId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{followRequestId}/accept")
    public ResponseEntity<?> acceptFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long followRequestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증 정보가 필요합니다."));
        }
        try {
            FollowResponseDto response = followService.respondToFollow(followRequestId, userDetails.getUsername(), true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Follow accept failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{followRequestId}/decline")
    public ResponseEntity<?> declineFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long followRequestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증 정보가 필요합니다."));
        }
        try {
            FollowResponseDto response = followService.respondToFollow(followRequestId, userDetails.getUsername(), false);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Follow decline failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/accepted")
    public ResponseEntity<?> getAcceptedFollows(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증 정보가 필요합니다."));
        }
        try {
            List<FollowSummaryDto> summaries = followService.getAcceptedFollows(userDetails.getUsername());
            return ResponseEntity.ok(summaries);
        } catch (IllegalArgumentException e) {
            log.warn("Accepted follow lookup failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
