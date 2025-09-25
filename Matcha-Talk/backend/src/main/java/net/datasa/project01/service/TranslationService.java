package net.datasa.project01.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class TranslationService {

    // application.properties에 설정한 Papago API 인증 정보를 주입받습니다.
    @Value("${papago.api.client-id}")
    private String clientId;

    @Value("${papago.api.client-secret}")
    private String clientSecret;

    @Value("${papago.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Papago API를 호출하여 텍스트를 번역합니다.
     * @param text 번역할 텍스트
     * @param sourceLang 원본 언어 코드 (예: "ko")
     * @param targetLang 목표 언어 코드 (예: "ja")
     * @return 번역된 텍스트
     */
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            // 1. HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-NCP-APIGW-API-KEY-ID", clientId); // 네이버 클라우드 플랫폼 헤더
            headers.set("X-NCP-APIGW-API-KEY", clientSecret); // 네이버 클라우드 플랫폼 헤더

            // 2. HTTP 요청 본문(Body) 설정
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("source", sourceLang);
            body.add("target", targetLang);
            body.add("text", text);

            // 3. 헤더와 본문을 합쳐 HTTP 요청 객체 생성
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // 4. Papago API에 POST 요청 전송 및 응답 받기
            String jsonResponse = restTemplate.postForObject(apiUrl, requestEntity, String.class);

            // 5. 받은 JSON 응답에서 번역된 텍스트만 추출
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            return rootNode.path("message").path("result").path("translatedText").asText();

        } catch (Exception e) {
            log.error("Papago API translation failed", e);
            return text; // 번역 실패 시 원본 텍스트를 그대로 반환
        }
    }
}