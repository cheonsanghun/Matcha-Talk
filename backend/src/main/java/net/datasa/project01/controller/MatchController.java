package net.datasa.project01.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.repository.MatchRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchRequestRepository matchRequestRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Long>> createMatchRequest(@RequestBody MatchRequestDto dto) throws JsonProcessingException {
        MatchRequest saved = matchRequestRepository.save(MatchRequest.builder()
                .choiceGender(dto.getChoiceGender())
                .minAge(dto.getMinAge())
                .maxAge(dto.getMaxAge())
                .regionCode(dto.getRegionCode())
                .interestsJson(objectMapper.writeValueAsString(dto.getInterestsJson()))
                .build());
        return ResponseEntity.ok(Map.of("requestId", saved.getMatchRequestId()));
    }
}
