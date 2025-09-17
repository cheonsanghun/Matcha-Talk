package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowRequestDto;
import net.datasa.project01.domain.dto.FollowUpdateDto;
import net.datasa.project01.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final UserService userService;

    /**
     * 팔로우 요청 생성 API
     * @param req 팔로우 요청 데이터 (followeeId 포함)
     * @param userDetails 현재 로그인된 사용자 정보
     * @return 성공 시 201 Created
     */
    @PostMapping
    public ResponseEntity<Void> createFollow(@Valid @RequestBody FollowRequestDto req,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        userService.createFollow(req, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 팔로우 요청 상태 변경 API (수락/거절)
     * @param followId 팔로우 관계 ID
     * @param dto 상태 변경 데이터 (status: "ACCEPTED" 또는 "REJECTED")
     * @param userDetails 현재 로그인된 사용자 정보
     * @return 성공 시 200 OK
     */
    @PutMapping("/{followId}/status")
    public ResponseEntity<Void> updateFollowStatus(@PathVariable Long followId,
                                                   @Valid @RequestBody FollowUpdateDto dto,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        userService.updateFollowStatus(followId, dto, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 팔로우 관계 삭제 API (언팔로우)
     * @param followId 팔로우 관계 ID
     * @param userDetails 현재 로그인된 사용자 정보
     * @return 성공 시 204 No Content
     */
    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> deleteFollow(@PathVariable Long followId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteFollow(followId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
