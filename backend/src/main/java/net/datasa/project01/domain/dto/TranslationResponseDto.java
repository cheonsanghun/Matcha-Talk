package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TranslationResponseDto {
    private final String translatedText;
    private final String sourceLang;
    private final String targetLang;
}
