package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslationRequestDto {
    @NotBlank
    private String text;

    @NotBlank
    private String sourceLang;

    @NotBlank
    private String targetLang;
}
