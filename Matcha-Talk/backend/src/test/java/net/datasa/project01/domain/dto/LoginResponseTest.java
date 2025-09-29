package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datasa.project01.domain.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginResponseSerializationIncludesUserPid() throws Exception {
        User user = User.builder()
                .userPid(42L)
                .loginId("tester")
                .nickName("Tester")
                .email("tester@example.com")
                .build();

        LoginResponse response = new LoginResponse(UserSummary.fromEntity(user));

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("user").path("userPid").asLong()).isEqualTo(42L);
        assertThat(root.path("user").path("id").asLong()).isEqualTo(42L);
    }
}
