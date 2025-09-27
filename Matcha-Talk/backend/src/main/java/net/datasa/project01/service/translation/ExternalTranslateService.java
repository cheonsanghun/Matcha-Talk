package net.datasa.project01.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.exception.FeatureUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ExternalTranslateService implements TranslateService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String provider;

    public ExternalTranslateService(@Value("${translate.base-url:}") String baseUrl,
                                     @Value("${translate.api-key:}") String apiKey,
                                     @Value("${translate.provider:generic}") String provider) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.provider = provider;
    }

    @Override
    public TranslationResult translate(String text, String sourceLang, String targetLang, @Nullable String context) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new FeatureUnavailableException("외부 번역 서비스가 비활성화되어 있습니다.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(apiKey)) {
                headers.setBearerAuth(apiKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("source", sourceLang);
            body.put("target", targetLang);
            if (StringUtils.hasText(context)) {
                body.put("context", context);
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("번역 API 호출이 실패했습니다.");
            }

            String translated = extractTranslatedText(response.getBody());
            return TranslationResult.builder()
                    .translatedText(translated)
                    .provider(provider)
                    .build();
        } catch (Exception e) {
            log.error("번역 API 호출 중 오류: {}", e.getMessage());
            throw new IllegalStateException("번역 요청이 실패했습니다.");
        }
    }

    private String extractTranslatedText(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // LibreTranslate: { "translatedText": "..." }
        JsonNode libre = root.path("translatedText");
        if (libre.isTextual()) {
            return libre.asText();
        }

        // Google v2: { "data": { "translations": [ { "translatedText": "..." } ] } }
        JsonNode google = root.path("data").path("translations");
        if (google.isArray() && google.size() > 0) {
            JsonNode node = google.get(0).path("translatedText");
            if (node.isTextual()) {
                return node.asText();
            }
        }

        // DeepL: { "translations": [ { "text": "..." } ] }
        JsonNode deepl = root.path("translations");
        if (deepl.isArray() && deepl.size() > 0) {
            JsonNode node = deepl.get(0).path("text");
            if (node.isTextual()) {
                return node.asText();
            }
        }

        throw new IllegalStateException("번역 결과를 파싱하지 못했습니다.");
    }
}
