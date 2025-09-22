package net.datasa.project01.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslationRequestDto {
    private String text;
    private String sourceLang;
    private String targetLang;
}
