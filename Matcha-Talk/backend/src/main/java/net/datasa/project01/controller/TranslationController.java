package net.datasa.project01.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.SavedWordResponseDto;
import net.datasa.project01.service.TranslationApplicationService;
import net.datasa.project01.service.translation.TranslationResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class TranslationController {

    private final TranslationApplicationService translationApplicationService;

    @PostMapping("/translate")
    public ResponseEntity<Map<String, Object>> translate(@AuthenticationPrincipal UserDetails principal,
                                                         @Valid @RequestBody TranslationRequest request) {
        TranslationResult result = translationApplicationService.translate(
                principal.getUsername(),
                request.getText(),
                request.getSourceLang(),
                request.getTargetLang(),
                request.getContext(),
                request.isSave()
        );

        return ResponseEntity.ok(Map.of(
                "translatedText", result.getTranslatedText(),
                "saved", request.isSave()
        ));
    }

    @GetMapping("/words")
    public ResponseEntity<List<SavedWordResponseDto>> listSavedWords(@AuthenticationPrincipal UserDetails principal,
                                                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                     LocalDateTime before) {
        List<SavedWordResponseDto> words = translationApplicationService.fetchSavedWords(principal.getUsername(), limit, before);
        return ResponseEntity.ok(words);
    }

    @DeleteMapping("/words/{wordId}")
    public ResponseEntity<Void> deleteSavedWord(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable Long wordId) {
        translationApplicationService.deleteSavedWord(principal.getUsername(), wordId);
        return ResponseEntity.noContent().build();
    }

    @Getter
    public static class TranslationRequest {
        @NotBlank
        private String text;

        @NotBlank
        private String sourceLang;

        @NotBlank
        private String targetLang;

        private boolean save;

        private String context;
    }
}
