package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.chat-requests-per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class ChatApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MessageRepository messages;

    @MockitoBean
    private AiServiceClient ai;

    @Test
    @Transactional
    void validatesCallsAiAndPersistsConversationWithSources() throws Exception {
        AiAnswer.Source source = new AiAnswer.Source(
                "chunk-19-2", "doc-1", "Quy chế đào tạo", "19", "2",
                "https://example.test/quy-che", "Sinh viên nộp hồ sơ tại PĐT.", 0.91);
        when(ai.answer(anyString())).thenReturn(new AiAnswer(
                "Nộp tại PĐT. [Nguồn 1]", "answered", List.of(source)));
        String token = register();

        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "   "))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "x".repeat(2001)))))
                .andExpect(status().isBadRequest());

        JsonNode response = response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "  Điều 19 nộp hồ sơ ở đâu?  "))), 200);
        UUID sessionId = UUID.fromString(response.get("sessionId").asText());
        assertThat(response.get("answer").asText()).contains("PĐT");
        assertThat(response.get("status").asText()).isEqualTo("answered");
        assertThat(response.at("/sources/0/chunk_id").asText()).isEqualTo("chunk-19-2");

        List<Message> history = messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                sessionId, PageRequest.of(0, 10)).getContent();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getContent()).isEqualTo("Điều 19 nộp hồ sơ ở đâu?");
        assertThat(history.get(0).getCitations()).isEmpty();
        assertThat(history.get(1).getCitations()).hasSize(1);
        assertThat(history.get(1).getCitations().get(0).get("chunk_id"))
                .isEqualTo("chunk-19-2");

        response(post("/api/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "question", "Câu hỏi tiếp theo", "sessionId", sessionId))), 200);
        assertThat(messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                sessionId, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(4);
    }

    @Test
    @Transactional
    void doesNotExposeAnotherUsersSession() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Trả lời", "answered", List.of()));
        String first = register();
        String second = register();
        JsonNode created = response(post("/api/chat")
                .header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu hỏi thứ nhất"))), 200);

        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "question", "Đọc session người khác",
                                "sessionId", created.get("sessionId").asText()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void listSessionsRequiresAuth() throws Exception {
        mvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void listSessionsReturnsOnlyOwnSessions() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Trả lời", "answered", List.of()));
        String userA = register();
        String userB = register();

        // userA tạo 2 session
        response(post("/api/chat").header("Authorization", "Bearer " + userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu hỏi A1"))), 200);
        response(post("/api/chat").header("Authorization", "Bearer " + userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu hỏi A2"))), 200);
        // userB tạo 1 session
        response(post("/api/chat").header("Authorization", "Bearer " + userB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu hỏi B1"))), 200);

        JsonNode sessionsA = response(get("/api/chat/sessions")
                .header("Authorization", "Bearer " + userA), 200);
        assertThat(sessionsA.get("totalElements").asInt()).isEqualTo(2);
        assertThat(sessionsA.get("content")).hasSize(2);
        // mỗi phần tử phải có các field cần thiết
        JsonNode firstSession = sessionsA.get("content").get(0);
        assertThat(firstSession.has("id")).isTrue();
        assertThat(firstSession.has("title")).isTrue();
        assertThat(firstSession.has("createdAt")).isTrue();
        assertThat(firstSession.has("updatedAt")).isTrue();

        JsonNode sessionsB = response(get("/api/chat/sessions")
                .header("Authorization", "Bearer " + userB), 200);
        assertThat(sessionsB.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @Transactional
    void listSessionsPaginationWorks() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Trả lời", "answered", List.of()));
        String token = register();
        response(post("/api/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu 1"))), 200);
        response(post("/api/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu 2"))), 200);
        response(post("/api/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu 3"))), 200);

        JsonNode page0 = response(get("/api/chat/sessions")
                .param("page", "0").param("size", "2")
                .header("Authorization", "Bearer " + token), 200);
        assertThat(page0.get("totalElements").asInt()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("content")).hasSize(2);

        JsonNode page1 = response(get("/api/chat/sessions")
                .param("page", "1").param("size", "2")
                .header("Authorization", "Bearer " + token), 200);
        assertThat(page1.get("content")).hasSize(1);
    }

    @Test
    @Transactional
    void getSessionDetailRequiresAuth() throws Exception {
        mvc.perform(get("/api/chat/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void getSessionDetailReturns404ForOtherUsersSession() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Trả lời", "answered", List.of()));
        String userA = register();
        String userB = register();
        JsonNode created = response(post("/api/chat")
                .header("Authorization", "Bearer " + userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Câu hỏi của A"))), 200);
        String sessionId = created.get("sessionId").asText();

        mvc.perform(get("/api/chat/sessions/" + sessionId)
                .header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getSessionDetailReturnsMessagesInOrder() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Câu trả lời 1", "answered", List.of()));
        String token = register();

        // Tạo session với câu hỏi đầu tiên
        JsonNode first = response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Hỏi điều 19"))), 200);
        String sessionId = first.get("sessionId").asText();

        // Hỏi tiếp trong cùng session
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Câu trả lời 2", "answered", List.of()));
        response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Hỏi điều 20", "sessionId", sessionId))), 200);

        JsonNode detail = response(get("/api/chat/sessions/" + sessionId)
                .header("Authorization", "Bearer " + token), 200);

        assertThat(detail.get("id").asText()).isEqualTo(sessionId);
        assertThat(detail.has("title")).isTrue();
        assertThat(detail.get("totalElements").asLong()).isEqualTo(4); // 2 user + 2 assistant
        assertThat(detail.get("messages")).hasSize(4);

        // Kiểm tra thứ tự: USER → ASSISTANT → USER → ASSISTANT
        assertThat(detail.get("messages").get(0).get("role").asText()).isEqualTo("USER");
        assertThat(detail.get("messages").get(1).get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(detail.get("messages").get(2).get("role").asText()).isEqualTo("USER");
        assertThat(detail.get("messages").get(3).get("role").asText()).isEqualTo("ASSISTANT");

        // Kiểm tra content
        assertThat(detail.get("messages").get(0).get("content").asText()).isEqualTo("Hỏi điều 19");
        assertThat(detail.get("messages").get(1).get("content").asText()).isEqualTo("Câu trả lời 1");
    }

    private String register() throws Exception {
        JsonNode response = response(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "email", UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "integration-password-123"))), 201);
        return response.get("accessToken").asText();
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int statusCode) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
