package net.datasa.project01.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.VocabularyEntryResponseDto;
import net.datasa.project01.service.VocabularyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
@Validated
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping
    public ResponseEntity<List<VocabularyEntryResponseDto>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(vocabularyService.list(requireUser(userDetails)));
    }

    @PostMapping
    public ResponseEntity<VocabularyEntryResponseDto> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid VocabularySaveRequest request) {

        return ResponseEntity.ok(
                vocabularyService.addWord(requireUser(userDetails), request.getOriginal(), request.getTranslated())
        );
    }

    private String requireUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("인증 정보가 필요합니다.");
        }
        return userDetails.getUsername();
    }

    public record VocabularySaveRequest(
            @NotBlank String original,
            @NotBlank String translated
    ) {
    }
}
