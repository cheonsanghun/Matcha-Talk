package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FollowListItemDto;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.service.FollowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetPid}")
    public ResponseEntity<FollowResponseDto> requestFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long targetPid
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        FollowResponseDto response = followService.requestFollow(userDetails.getUsername(), targetPid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{followId}/accept")
    public ResponseEntity<FollowResponseDto> acceptFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long followId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        FollowResponseDto response = followService.approveFollow(userDetails.getUsername(), followId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{followId}/reject")
    public ResponseEntity<FollowResponseDto> rejectFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long followId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        FollowResponseDto response = followService.rejectFollow(userDetails.getUsername(), followId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> removeFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long followId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        followService.removeFollow(userDetails.getUsername(), followId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/followings")
    public ResponseEntity<List<FollowListItemDto>> getFollowings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "status", required = false) Follow.Status status
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<FollowListItemDto> items = followService.getFollowings(userDetails.getUsername(), status);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowListItemDto>> getFollowers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "status", required = false) Follow.Status status
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<FollowListItemDto> items = followService.getFollowers(userDetails.getUsername(), status);
        return ResponseEntity.ok(items);
    }
}
