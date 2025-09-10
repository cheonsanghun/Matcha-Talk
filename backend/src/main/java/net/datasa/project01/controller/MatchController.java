package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchRequestRepository matchRequestRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Long>> createMatchRequest(@RequestBody MatchRequestDto dto) throws JsonProcessingException {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userPid = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."))
                .getUserPid();

        MatchRequest saved = matchRequestRepository.save(MatchRequest.builder()
                .userPid(userPid)
                .choiceGender(dto.getChoiceGender())
                .minAge(dto.getMinAge())
                .maxAge(dto.getMaxAge())
                .regionCode(dto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(dto.getInterestsJson()))
                .status(dto.getStatus() != null ? dto.getStatus() : "WAITING")
                .build());

        return ResponseEntity.ok(Map.of("requestId", saved.getMatchRequestId()));
    }
}

