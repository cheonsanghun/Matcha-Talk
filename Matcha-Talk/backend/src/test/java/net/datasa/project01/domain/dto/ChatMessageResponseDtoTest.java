package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageResponseDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesSenderLoginId() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ChatMessageResponseDto dto = ChatMessageResponseDto.builder()
                .roomId(1L)
                .senderLoginId("user123")
                .senderNickName("테스터")
                .senderLanguageCode("ko")
                .content("hello")
                .contentType("TEXT")
                .fileName(null)
                .fileUrl(null)
                .mimeType(null)
                .sizeBytes(null)
                .sentAt(now)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("senderLoginId").asText()).isEqualTo("user123");
    }
}
