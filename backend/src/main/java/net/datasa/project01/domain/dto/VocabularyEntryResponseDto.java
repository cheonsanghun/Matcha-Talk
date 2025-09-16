package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.datasa.project01.domain.entity.VocabularyEntry;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class VocabularyEntryResponseDto {

    private final Long id;
    private final String originalText;
    private final String translatedText;
    private final LocalDateTime savedAt;

    public static VocabularyEntryResponseDto from(VocabularyEntry entry) {
        return new VocabularyEntryResponseDto(
                entry.getId(),
                entry.getOriginalText(),
                entry.getTranslatedText(),
                entry.getCreatedAt()
        );
    }
}
