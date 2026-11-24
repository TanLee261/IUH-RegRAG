package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies backend chat orchestration with the MockAiServiceClient (no external LLM calls).
 * Ensures CI pipeline stability and zero external cost.
 */
@SpringBootTest(properties = "app.security.chat-requests-per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles({"db", "mock-ai"})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class ChatOrchestrationWithMockAiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MockAiServiceClient mockAi;

    @BeforeEach
    void resetMockAi() {
        mockAi.reset();
    }

    @Test
    @Transactional
    @DisplayName("Orchestration: Mock AI responds with accurate regulations without touching real LLM")
    void orchestratesChatWithMockAiRegulations() throws Exception {
        String token = registerStudent();

        // 1. Ask graduation requirement question -> Mock AI provides Regulation Article 22
        JsonNode res = execute(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Điều kiện làm khóa luận tốt nghiệp IUH?"))), 200);

        assertThat(res.get("answer").asText()).contains("Điều 22");
        assertThat(res.get("status").asText()).isEqualTo("answered");
        assertThat(res.at("/sources/0/dieu").asText()).isEqualTo("22");
        assertThat(mockAi.getRecordedQuestions()).contains("Điều kiện làm khóa luận tốt nghiệp IUH?");

        // 2. Ask warning/expulsion question -> Mock AI provides Regulation Article 18
        JsonNode warningRes = execute(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Bao nhiêu lần bị cảnh báo thì buộc thôi học?"))), 200);

        assertThat(warningRes.get("answer").asText()).contains("Điều 18");
        assertThat(warningRes.at("/sources/0/dieu").asText()).isEqualTo("18");

        // 3. Ask out-of-domain question -> Mock AI returns insufficient_context with 0 citations
        JsonNode oodRes = execute(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Dự báo thời tiết ngày mai thế nào?"))), 200);

        assertThat(oodRes.get("status").asText()).isEqualTo("insufficient_context");
        assertThat(oodRes.get("sources")).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Orchestration: Gracefully handles Mock AI service failure returning 502 Bad Gateway")
    void handlesAiServiceFailureGracefully() throws Exception {
        String token = registerStudent();

        // Simulate AI service failure (down / timeout)
        mockAi.setSimulateFailure(true);

        mvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "Có được đăng ký 30 tín chỉ không?"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message").value("Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau."));
    }

    private String registerStudent() throws Exception {
        JsonNode res = execute(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "email", "mockai." + UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "MockAiPassword123!"))), 201);
        return res.get("accessToken").asText();
    }

    private String body(Object obj) throws Exception {
        return json.writeValueAsString(obj);
    }

    private JsonNode execute(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder,
            int expectedStatus) throws Exception {
        var result = mvc.perform(requestBuilder)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? null : json.readTree(content);
    }
}
