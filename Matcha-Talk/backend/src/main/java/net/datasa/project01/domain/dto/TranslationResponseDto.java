package net.datasa.project01.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TranslationResponseDto {
    private final Long roomId;
    private final Long messageId;
    private final String originalText;
    private final String translatedText;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final boolean translated;
}
