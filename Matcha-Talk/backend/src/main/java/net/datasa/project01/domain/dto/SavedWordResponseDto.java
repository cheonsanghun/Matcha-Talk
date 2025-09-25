package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.SavedWord;

import java.time.format.DateTimeFormatter;

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
    private final String createdAt;

    public static SavedWordResponseDto fromEntity(SavedWord savedWord) {
        return SavedWordResponseDto.builder()
                .wordId(savedWord.getWordId())
                .sourceText(savedWord.getSourceText())
                .translatedText(savedWord.getTranslatedText())
                .sourceLang(savedWord.getSourceLang())
                .targetLang(savedWord.getTargetLang())
                .context(savedWord.getContext())
                .createdAt(savedWord.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();
    }
}
