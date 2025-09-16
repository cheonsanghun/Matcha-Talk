package net.datasa.project01.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.service.FollowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
@Slf4j
@Validated
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetLoginId}")
    public ResponseEntity<FollowResponseDto> follow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable @NotBlank String targetLoginId) {

        String loginId = requireUser(userDetails);
        return ResponseEntity.ok(followService.follow(loginId, targetLoginId));
    }

    @DeleteMapping("/{targetLoginId}")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable @NotBlank String targetLoginId) {

        String loginId = requireUser(userDetails);
        followService.unfollow(loginId, targetLoginId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FollowResponseDto>> getFollowing(
            @AuthenticationPrincipal UserDetails userDetails) {

        String loginId = requireUser(userDetails);
        return ResponseEntity.ok(followService.getFollowing(loginId));
    }

    private String requireUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("인증 정보가 필요합니다.");
        }
        return userDetails.getUsername();
    }
}
