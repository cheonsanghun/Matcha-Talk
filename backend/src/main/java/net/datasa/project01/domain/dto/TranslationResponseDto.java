package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TranslationResponseDto {
    private final String originalText;
    private final String translatedText;
    private final String sourceLang;
    private final String targetLang;
}
