package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavedWordRequestDto {
    @NotBlank
    private String sourceText;

    @NotBlank
    private String translatedText;

    @NotBlank
    private String sourceLang;

    @NotBlank
    private String targetLang;

    private String context;
}
