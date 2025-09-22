package net.datasa.project01.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FriendResponseDto;
import net.datasa.project01.service.FriendshipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @GetMapping
    public ResponseEntity<List<FriendResponseDto>> list(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<FriendResponseDto> friends = friendshipService.list(principal.getName());
        return ResponseEntity.ok(friends);
    }

    @PostMapping("/{targetLoginId}")
    public ResponseEntity<FriendResponseDto> follow(@PathVariable String targetLoginId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        FriendResponseDto dto = friendshipService.follow(principal.getName(), targetLoginId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{targetLoginId}")
    public ResponseEntity<Void> unfollow(@PathVariable String targetLoginId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        friendshipService.unfollow(principal.getName(), targetLoginId);
        return ResponseEntity.noContent().build();
    }
}
