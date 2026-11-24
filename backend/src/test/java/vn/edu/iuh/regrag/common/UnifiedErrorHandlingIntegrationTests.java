package vn.edu.iuh.regrag.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.iuh.regrag.chat.AiServiceClient;

@SpringBootTest(properties = "app.security.chat-requests-per-minute=2")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class UnifiedErrorHandlingIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AiServiceClient aiService;

    private String registerStudent() throws Exception {
        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", UUID.randomUUID() + "@iuh.edu.vn",
                                "password", "integration-password-123"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    @Test
    @Transactional
    void handle400ValidationErrorWithFieldDetails() throws Exception {
        String token = registerStudent();

        var result = mvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "   "))))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("error").asText()).isEqualTo("Bad Request");
        assertThat(body.get("message").asText()).contains("không hợp lệ");
        assertThat(body.get("path").asText()).isEqualTo("/api/chat");
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("errors")).isTrue();
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional
    void handle401UnauthorizedReturnsUnifiedJson() throws Exception {
        var result = mvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).contains("Yêu cầu đăng nhập");
        assertThat(body.get("path").asText()).isEqualTo("/api/chat/sessions");
    }

    @Test
    @Transactional
    void handle403ForbiddenReturnsUnifiedJson() throws Exception {
        String studentToken = registerStudent();

        var result = mvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).contains("không có quyền");
        assertThat(body.get("path").asText()).isEqualTo("/api/documents");
    }

    @Test
    @Transactional
    void handle404NotFoundReturnsUnifiedJson() throws Exception {
        String token = registerStudent();
        UUID nonexistentId = UUID.randomUUID();

        var result = mvc.perform(get("/api/chat/sessions/" + nonexistentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("error").asText()).isEqualTo("Not Found");
        assertThat(body.has("message")).isTrue();
    }

    @Test
    @Transactional
    void handle429RateLimitReturnsUnifiedJsonAndRetryAfter() throws Exception {
        when(aiService.answer(anyString())).thenReturn(
                new vn.edu.iuh.regrag.chat.AiAnswer("OK", "answered", java.util.List.of()));

        String token = registerStudent();
        String payload = json.writeValueAsString(Map.of("question", "Câu hỏi kiểm tra rate limit"));

        // Call 1: OK
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        // Call 2: OK (limit = 2)
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        // Call 3: Rate limited -> 429
        var result = mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("error").asText()).isEqualTo("Too Many Requests");
        assertThat(body.get("message").asText()).contains("quá nhiều yêu cầu");
    }

    @Test
    @Transactional
    void handle502AiServiceDownReturnsFriendlyMessage() throws Exception {
        when(aiService.answer(anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable"));

        String token = registerStudent();
        var result = mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "Khi nào tốt nghiệp?"))))
                .andExpect(status().isBadGateway())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(502);
        assertThat(body.get("error").asText()).isEqualTo("Bad Gateway");
        assertThat(body.get("message").asText()).contains("Dịch vụ AI hiện không khả dụng");
    }
}
