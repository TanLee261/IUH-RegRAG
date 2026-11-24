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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end integration test verifying the complete user journey:
 * Register & Login → Ask Chat Question → Read History → Submit & Update Feedback → Logout.
 */
@SpringBootTest(properties = "app.security.chat-requests-per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class LoginChatFeedbackIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AiServiceClient ai;

    @Test
    @Transactional
    @DisplayName("Complete User Journey: Register → Login → Chat Multi-turn → Feedback Upsert → Logout")
    void completeUserJourney_Login_Chat_Feedback_Logout() throws Exception {
        String studentEmail = "student." + UUID.randomUUID() + "@iuh.edu.vn";
        String password = "SecureStudentPassword@2024";

        // ── 1. Register new student account ──
        JsonNode registerRes = execute(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", studentEmail, "password", password))), 201);
        String initialToken = registerRes.get("accessToken").asText();
        assertThat(initialToken).isNotBlank();
        assertThat(registerRes.get("tokenType").asText()).isEqualTo("Bearer");

        // ── 2. Login with credentials to obtain fresh session tokens ──
        JsonNode loginRes = execute(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", studentEmail, "password", password))), 200);
        String accessToken = loginRes.get("accessToken").asText();
        String refreshToken = loginRes.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        // ── 3. Chat: Ask initial question (creates a new chat session) ──
        AiAnswer.Source source = new AiAnswer.Source(
                "chunk-dieu-22-1", "doc-dao-tao-2024", "Quy chế đào tạo ĐHCN",
                "22", "1", "https://iuh.edu.vn/quy-che-2024.pdf",
                "Sinh viên tích lũy đủ 110 tín chỉ và ĐTB >= 2.5 được đăng ký làm khóa luận.",
                0.95);
        when(ai.answer("Điều kiện làm khóa luận tốt nghiệp là gì?")).thenReturn(new AiAnswer(
                "Theo Điều 22 Quy chế đào tạo, sinh viên tích lũy từ 110 tín chỉ trở lên và ĐTB từ 2.5 được đăng ký khóa luận. [Nguồn 1]",
                "answered",
                List.of(source)));

        JsonNode chatRes1 = execute(post("/api/chat")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("question", "Điều kiện làm khóa luận tốt nghiệp là gì?"))), 200);

        UUID sessionId = UUID.fromString(chatRes1.get("sessionId").asText());
        UUID userMsgId = UUID.fromString(chatRes1.get("userMessageId").asText());
        UUID assistantMsgId = UUID.fromString(chatRes1.get("assistantMessageId").asText());

        assertThat(sessionId).isNotNull();
        assertThat(userMsgId).isNotNull();
        assertThat(assistantMsgId).isNotNull();
        assertThat(chatRes1.get("answer").asText()).contains("Điều 22");
        assertThat(chatRes1.get("status").asText()).isEqualTo("answered");
        assertThat(chatRes1.at("/sources/0/chunk_id").asText()).isEqualTo("chunk-dieu-22-1");

        // ── 4. Verify Session History List & Detail ──
        JsonNode sessionList = execute(get("/api/chat/sessions")
                .header("Authorization", "Bearer " + accessToken), 200);
        assertThat(sessionList.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode sessionDetail = execute(get("/api/chat/sessions/" + sessionId)
                .header("Authorization", "Bearer " + accessToken), 200);
        assertThat(sessionDetail.get("id").asText()).isEqualTo(sessionId.toString());
        assertThat(sessionDetail.get("messages")).hasSize(2);
        assertThat(sessionDetail.get("messages").get(0).get("role").asText()).isEqualTo("USER");
        assertThat(sessionDetail.get("messages").get(1).get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(sessionDetail.get("messages").get(1).get("id").asText()).isEqualTo(assistantMsgId.toString());

        // ── 5. Chat Multi-turn: Follow-up question in the same session ──
        when(ai.answer("Nếu không đủ 2.5 thì làm môn gì thay thế?")).thenReturn(new AiAnswer(
                "Sinh viên có thể đăng ký 2 học phần chuyên ngành thay thế khóa luận theo hướng dẫn của Khoa.",
                "answered",
                List.of()));

        JsonNode chatRes2 = execute(post("/api/chat")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "question", "Nếu không đủ 2.5 thì làm môn gì thay thế?",
                        "sessionId", sessionId))), 200);

        assertThat(chatRes2.get("sessionId").asText()).isEqualTo(sessionId.toString());
        assertThat(chatRes2.get("answer").asText()).contains("học phần chuyên ngành thay thế");

        JsonNode updatedSessionDetail = execute(get("/api/chat/sessions/" + sessionId)
                .header("Authorization", "Bearer " + accessToken), 200);
        assertThat(updatedSessionDetail.get("messages")).hasSize(4); // 2 user + 2 assistant

        // ── 6. Feedback: Submit positive feedback for the first assistant response ──
        JsonNode feedbackSubmitRes = execute(post("/api/feedback")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "messageId", assistantMsgId,
                        "rating", "HELPFUL",
                        "comment", "Trích dẫn Điều 22 rất chuẩn xác và rõ ràng."))), 200);

        assertThat(feedbackSubmitRes.get("messageId").asText()).isEqualTo(assistantMsgId.toString());
        assertThat(feedbackSubmitRes.get("rating").asText()).isEqualTo("HELPFUL");
        assertThat(feedbackSubmitRes.get("comment").asText()).contains("rất chuẩn xác");

        // ── 7. Feedback: Read back the submitted feedback ──
        JsonNode getFeedbackRes = execute(get("/api/feedback/messages/" + assistantMsgId)
                .header("Authorization", "Bearer " + accessToken), 200);
        assertThat(getFeedbackRes.get("rating").asText()).isEqualTo("HELPFUL");
        assertThat(getFeedbackRes.get("comment").asText()).contains("rất chuẩn xác");

        // ── 8. Feedback: Update/Upsert feedback rating and comment ──
        JsonNode feedbackUpdateRes = execute(post("/api/feedback")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "messageId", assistantMsgId,
                        "rating", "NOT_HELPFUL",
                        "comment", "Cần thêm ví dụ điều kiện cho hệ liên thông."))), 200);

        assertThat(feedbackUpdateRes.get("rating").asText()).isEqualTo("NOT_HELPFUL");
        assertThat(feedbackUpdateRes.get("comment").asText()).contains("hệ liên thông");

        JsonNode readUpdatedFeedback = execute(get("/api/feedback/messages/" + assistantMsgId)
                .header("Authorization", "Bearer " + accessToken), 200);
        assertThat(readUpdatedFeedback.get("rating").asText()).isEqualTo("NOT_HELPFUL");

        // ── 9. Security isolation: Another user cannot view or feedback this message ──
        String otherUserToken = registerOtherUser();
        mvc.perform(get("/api/chat/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "messageId", assistantMsgId,
                                "rating", "HELPFUL",
                                "comment", "Gian lận vote chéo"))))
                .andExpect(status().isNotFound());

        // ── 10. Logout: Revoke tokens and verify subsequent calls are denied ──
        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "Hỏi sau khi logout"))))
                .andExpect(status().isUnauthorized());
    }

    private String registerOtherUser() throws Exception {
        JsonNode res = execute(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "email", "other." + UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "OtherPassword@2024"))), 201);
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
