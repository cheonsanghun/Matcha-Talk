package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class SavedWordResponseDto {
    private final Long wordId;
    private final String sourceText;
    private final String translatedText;
    private final String sourceLang;
    private final String targetLang;
    private final String context;
    private final LocalDateTime createdAt;
}
