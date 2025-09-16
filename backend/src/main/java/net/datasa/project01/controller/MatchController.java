package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.repository.MatchRequestRepository;
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

    /**
     * 랜덤 매칭을 요청하는 API 엔드포인트
     * @param userDetails 현재 인증된 사용자의 정보 (Spring Security가 주입)
     * @param dto 프론트엔드에서 보낸 매칭 조건
     * @return 요청 접수 결과
     */
    @PostMapping("/requests")
    public ResponseEntity<String> startRandomMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MatchRequestDto dto) {

        try {
            String loginId = userDetails.getUsername();
            log.info("Match request received from user: {}", loginId);
            matchService.startOrFindMatch(loginId, dto);

            // TODO: MatchService의 결과에 따라 다른 응답 반환 (대기열 등록 or 매칭 성공)
            return ResponseEntity.ok("매칭 요청이 성공적으로 접수되었습니다.");

        } catch (JsonProcessingException e) {
            log.error("JSON processing error during match request for user: {}", userDetails.getUsername(), e);
            return ResponseEntity.internalServerError().body("매칭 요청 처리 중 오류가 발생했습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid match request from user: {}. Reason: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}