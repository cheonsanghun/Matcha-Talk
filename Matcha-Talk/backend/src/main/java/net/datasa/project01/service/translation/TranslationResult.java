package net.datasa.project01.service.translation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TranslationResult {
    private final String translatedText;
    private final String provider;
}
