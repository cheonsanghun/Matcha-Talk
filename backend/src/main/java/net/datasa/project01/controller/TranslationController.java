package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.TranslationRequestDto;
import net.datasa.project01.domain.dto.TranslationResponseDto;
import net.datasa.project01.service.TranslationService;
import net.datasa.project01.service.VocabularyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;
    private final VocabularyService vocabularyService;

    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TranslationRequestDto requestDto) {

        String translated = translationService.translate(
                requestDto.getText(),
                requestDto.getSourceLang(),
                requestDto.getTargetLang()
        );

        boolean saved = false;
        if (requestDto.isSave() && userDetails != null) {
            vocabularyService.addWord(
                    userDetails.getUsername(),
                    requestDto.getText(),
                    translated
            );
            saved = true;
        }

        return ResponseEntity.ok(new TranslationResponseDto(translated, saved));
    }
}
