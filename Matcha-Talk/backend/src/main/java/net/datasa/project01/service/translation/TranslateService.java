package net.datasa.project01.service.translation;

import org.springframework.lang.Nullable;

public interface TranslateService {

    TranslationResult translate(String text, String sourceLang, String targetLang, @Nullable String context);
}
