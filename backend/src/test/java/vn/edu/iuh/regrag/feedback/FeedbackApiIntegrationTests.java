package vn.edu.iuh.regrag.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.chat.AiAnswer;
import vn.edu.iuh.regrag.chat.AiServiceClient;

@SpringBootTest(properties = "app.security.chat-requests-per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class FeedbackApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AiServiceClient ai;

    @Test
    @Transactional
    void unauthenticatedCannotAccessFeedbackEndpoints() throws Exception {
        mvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "messageId", UUID.randomUUID(),
                                "rating", "HELPFUL"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/feedback/messages/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void cannotSubmitFeedbackForAnotherUsersMessage() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Câu trả lời cho User 1", "answered", List.of()));
        String tokenUser1 = register();
        String tokenUser2 = register();

        JsonNode chatResponse = response(post("/api/chat")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", "Học bổng điều kiện gì?"))), 200);

        String assistantMsgId = chatResponse.get("assistantMessageId").asText();

        // User 2 attempts to feedback User 1's assistant message
        mvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "messageId", assistantMsgId,
                                "rating", "HELPFUL",
                                "comment", "Gian lận vote"))))
                .andExpect(status().isNotFound());

        // User 2 attempts to read User 1's feedback
        mvc.perform(get("/api/feedback/messages/" + assistantMsgId)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void cannotSubmitFeedbackForUserMessageRole() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Câu trả lời", "answered", List.of()));
        String token = register();

        JsonNode chatResponse = response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", "Câu hỏi kiểm tra role"))), 200);

        String userMsgId = chatResponse.get("userMessageId").asText();

        // Submitting feedback on USER's own question message is forbidden by business rule
        mvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "messageId", userMsgId,
                                "rating", "HELPFUL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void submitAndUpsertFeedbackSuccessfully() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Câu trả lời tốt", "answered", List.of()));
        String token = register();

        JsonNode chatResponse = response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", "Điều kiện tốt nghiệp?"))), 200);

        String assistantMsgId = chatResponse.get("assistantMessageId").asText();

        // 1. Submit initial feedback HELPFUL
        JsonNode firstFeedback = response(post("/api/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "messageId", assistantMsgId,
                        "rating", "HELPFUL",
                        "comment", "Rất rõ ràng"))), 200);

        assertThat(firstFeedback.get("id").asText()).isNotBlank();
        assertThat(firstFeedback.get("messageId").asText()).isEqualTo(assistantMsgId);
        assertThat(firstFeedback.get("rating").asText()).isEqualTo("HELPFUL");
        assertThat(firstFeedback.get("comment").asText()).isEqualTo("Rất rõ ràng");

        String feedbackId = firstFeedback.get("id").asText();

        // 2. Query feedback by messageId
        JsonNode fetched = response(get("/api/feedback/messages/" + assistantMsgId)
                .header("Authorization", "Bearer " + token), 200);
        assertThat(fetched.get("id").asText()).isEqualTo(feedbackId);
        assertThat(fetched.get("rating").asText()).isEqualTo("HELPFUL");

        // 3. Upsert: change rating to NOT_HELPFUL and update comment
        JsonNode updatedFeedback = response(post("/api/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "messageId", assistantMsgId,
                        "rating", "NOT_HELPFUL",
                        "comment", "Thiếu điều khoản bổ sung"))), 200);

        // ID must be the same (upserted record, not duplicate)
        assertThat(updatedFeedback.get("id").asText()).isEqualTo(feedbackId);
        assertThat(updatedFeedback.get("rating").asText()).isEqualTo("NOT_HELPFUL");
        assertThat(updatedFeedback.get("comment").asText()).isEqualTo("Thiếu điều khoản bổ sung");
    }

    @Test
    @Transactional
    void getFeedbackReturns404WhenNoFeedbackSubmittedYet() throws Exception {
        when(ai.answer(anyString())).thenReturn(new AiAnswer("Trả lời", "answered", List.of()));
        String token = register();

        JsonNode chatResponse = response(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", "Câu hỏi chưa feedback"))), 200);

        String assistantMsgId = chatResponse.get("assistantMessageId").asText();

        mvc.perform(get("/api/feedback/messages/" + assistantMsgId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String register() throws Exception {
        JsonNode response = response(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "integration-password-123"))), 201);
        return response.get("accessToken").asText();
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int statusCode) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
