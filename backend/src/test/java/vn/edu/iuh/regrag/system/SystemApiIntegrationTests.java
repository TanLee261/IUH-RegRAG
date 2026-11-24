package vn.edu.iuh.regrag.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.chat.AiServiceClient;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class SystemApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private AiServiceClient aiService;

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
    void healthEndpointIsPublicAndReturnsHealthStatus() throws Exception {
        when(aiService.isHealthy()).thenReturn(true);

        var result = mvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.at("/components/database/status").asText()).isEqualTo("UP");
        assertThat(body.at("/components/aiService/status").asText()).isEqualTo("UP");
    }

    @Test
    @Transactional
    void healthEndpointReportsDegradedWhenAiServiceIsDown() throws Exception {
        when(aiService.isHealthy()).thenReturn(false);

        var result = mvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("DEGRADED");
        assertThat(body.at("/components/database/status").asText()).isEqualTo("UP");
        assertThat(body.at("/components/aiService/status").asText()).isEqualTo("DOWN");
    }

    @Test
    @Transactional
    void versionEndpointIsPublic() throws Exception {
        var result = mvc.perform(get("/api/system/version"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("name").asText()).isEqualTo("iuh-regrag-backend");
        assertThat(body.get("version").asText()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(body.get("status").asText()).isEqualTo("running");
    }

    @Test
    @Transactional
    void vectorDocumentsEndpointRequiresAdmin() throws Exception {
        // 1. Unauthenticated gets 401
        mvc.perform(get("/api/system/documents"))
                .andExpect(status().isUnauthorized());

        // 2. Student gets 403
        String studentToken = registerStudent();
        mvc.perform(get("/api/system/documents")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 3. Admin gets 200
        String adminToken = registerAdmin();
        var result = mvc.perform(get("/api/system/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
    }
}
