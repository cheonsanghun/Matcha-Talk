package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslationRequestDto {

    @NotBlank
    private String text;

    private String sourceLang = "auto";

    @NotBlank
    private String targetLang;

    private boolean save;
}
