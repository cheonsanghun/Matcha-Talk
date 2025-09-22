package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.TranslationRequestDto;
import net.datasa.project01.domain.dto.TranslationResponseDto;
import net.datasa.project01.service.TranslationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(@Valid @RequestBody TranslationRequestDto requestDto) {
        String translated = translationService.translate(
                requestDto.getText(),
                requestDto.getSourceLang(),
                requestDto.getTargetLang()
        );
        TranslationResponseDto response = TranslationResponseDto.builder()
                .originalText(requestDto.getText())
                .translatedText(translated)
                .sourceLang(requestDto.getSourceLang())
                .targetLang(requestDto.getTargetLang())
                .build();
        return ResponseEntity.ok(response);
    }
}
