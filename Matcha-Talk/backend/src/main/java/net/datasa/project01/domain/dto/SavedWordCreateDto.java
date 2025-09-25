package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.SavedWord;
import net.datasa.project01.domain.entity.User;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavedWordCreateDto {

    private String sourceText;
    private String translatedText;
    private String sourceLang;
    private String targetLang;
    private String context;

    public SavedWord toEntity(User user) {
        return SavedWord.builder()
                .user(user)
                .sourceText(this.sourceText)
                .translatedText(this.translatedText)
                .sourceLang(this.sourceLang)
                .targetLang(this.targetLang)
                .context(this.context)
                .build();
    }
}
