package net.datasa.project01.controller;

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
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(@RequestBody TranslationRequestDto requestDto) {
        String text = requestDto.getText();
        if (text == null || text.isBlank()) {
            return ResponseEntity.ok(new TranslationResponseDto("", requestDto.getSourceLang(), requestDto.getTargetLang()));
        }

        String source = requestDto.getSourceLang();
        String target = requestDto.getTargetLang();
        if (source == null || source.isBlank()) {
            source = "ko";
        }
        if (target == null || target.isBlank()) {
            target = "en";
        }

        String translated = translationService.translate(text, source, target);
        return ResponseEntity.ok(new TranslationResponseDto(translated, source, target));
    }
}
