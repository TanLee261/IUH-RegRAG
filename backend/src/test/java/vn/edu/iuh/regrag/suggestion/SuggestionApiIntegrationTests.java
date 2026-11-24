package vn.edu.iuh.regrag.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class SuggestionApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    private String registerStudent() throws Exception {
        JsonNode response = response(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "integration-password-123"))), 201);
        return response.get("accessToken").asText();
    }

    private String registerAdmin() throws Exception {
        String token = registerStudent();
        UUID userId = UUID.fromString(decodeSubject(token));
        var user = users.findById(userId).orElseThrow();
        user.setRole(UserRole.ADMIN);
        users.saveAndFlush(user);
        return token;
    }

    private String decodeSubject(String token) throws Exception {
        String payload = token.split("\\.")[1];
        int padding = (4 - payload.length() % 4) % 4;
        payload = payload + "=".repeat(padding);
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        JsonNode node = json.readTree(new String(decoded, StandardCharsets.UTF_8));
        return node.get("sub").asText();
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int statusCode) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Transactional
    void publicCanListSuggestionsWithoutAuth() throws Exception {
        // Without Authorization header, GET /api/suggestions must return 200 OK
        var result = mvc.perform(get("/api/suggestions"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = json.readTree(result.getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        // Since V3 migration seeds 8 questions across 4 topics, list should have suggestions
        assertThat(list.size()).isGreaterThanOrEqualTo(8);
        JsonNode first = list.get(0);
        assertThat(first.has("id")).isTrue();
        assertThat(first.has("topic")).isTrue();
        assertThat(first.has("question")).isTrue();
        assertThat(first.get("active").asBoolean()).isTrue();
    }

    @Test
    @Transactional
    void unauthenticatedCannotManageSuggestions() throws Exception {
        UUID randomId = UUID.randomUUID();
        mvc.perform(post("/api/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "topic", "Tốt nghiệp",
                                "question", "Câu hỏi test?"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/suggestions/" + randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "topic", "Tốt nghiệp",
                                "question", "Câu hỏi sửa?"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/suggestions/" + randomId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void studentCannotManageSuggestions() throws Exception {
        String token = registerStudent();
        UUID randomId = UUID.randomUUID();

        mvc.perform(post("/api/suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "topic", "Tốt nghiệp",
                                "question", "Câu hỏi test?"))))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/suggestions/" + randomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "topic", "Tốt nghiệp",
                                "question", "Câu hỏi sửa?"))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/suggestions/" + randomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanPerformCrudOnSuggestions() throws Exception {
        String adminToken = registerAdmin();

        // 1. Create a new suggestion
        JsonNode created = response(post("/api/suggestions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "topic", "Tốt nghiệp",
                        "question", "Khi nào có đợt xét tốt nghiệp đợt 2?",
                        "displayOrder", 10,
                        "active", true))), 201);

        String id = created.get("id").asText();
        assertThat(id).isNotBlank();
        assertThat(created.get("topic").asText()).isEqualTo("Tốt nghiệp");
        assertThat(created.get("question").asText()).isEqualTo("Khi nào có đợt xét tốt nghiệp đợt 2?");
        assertThat(created.get("displayOrder").asInt()).isEqualTo(10);
        assertThat(created.get("active").asBoolean()).isTrue();

        // 2. Get single suggestion
        JsonNode fetched = response(get("/api/suggestions/" + id)
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(fetched.get("id").asText()).isEqualTo(id);

        // 3. Update the suggestion
        JsonNode updated = response(put("/api/suggestions/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "topic", "Tốt nghiệp",
                        "question", "Lịch xét tốt nghiệp đợt 2 được công bố ở đâu?",
                        "displayOrder", 12,
                        "active", false))), 200);

        assertThat(updated.get("question").asText()).isEqualTo("Lịch xét tốt nghiệp đợt 2 được công bố ở đâu?");
        assertThat(updated.get("displayOrder").asInt()).isEqualTo(12);
        assertThat(updated.get("active").asBoolean()).isFalse();

        // 4. Verify public list does not include inactive suggestion
        JsonNode publicList = response(get("/api/suggestions"), 200);
        boolean containsInactive = false;
        for (JsonNode item : publicList) {
            if (id.equals(item.get("id").asText())) {
                containsInactive = true;
                break;
            }
        }
        assertThat(containsInactive).isFalse();

        // 5. Verify admin listAll includes the inactive suggestion
        JsonNode allList = response(get("/api/suggestions/all")
                .header("Authorization", "Bearer " + adminToken), 200);
        boolean foundInAll = false;
        for (JsonNode item : allList) {
            if (id.equals(item.get("id").asText())) {
                foundInAll = true;
                break;
            }
        }
        assertThat(foundInAll).isTrue();

        // 6. Delete the suggestion
        mvc.perform(delete("/api/suggestions/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 7. Verify 404 after delete
        mvc.perform(get("/api/suggestions/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
