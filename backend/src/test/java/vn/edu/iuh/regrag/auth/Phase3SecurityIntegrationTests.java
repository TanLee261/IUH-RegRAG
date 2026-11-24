package vn.edu.iuh.regrag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;
import vn.edu.iuh.regrag.chat.AiAnswer;
import vn.edu.iuh.regrag.chat.AiServiceClient;
import vn.edu.iuh.regrag.document.DocumentService;
import vn.edu.iuh.regrag.document.DocumentStorageService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.security.chat-requests-per-minute=2")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@Import(Phase3SecurityIntegrationTests.TestEndpoints.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class Phase3SecurityIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private LlmCredentials llmCredentials;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AiServiceClient aiService;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @Test
    @Transactional
    void onlyAdminCanAccessDocumentAndAdminRoutes() throws Exception {
        JsonNode student = register();
        String token = student.get("accessToken").asText();

        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/42").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        var user = users.findById(UUID.fromString(jwtDecoder.decode(token).getSubject())).orElseThrow();
        user.setRole(UserRole.ADMIN);
        users.flush();

        // The filter re-reads the role from the database on each request.
        // DocumentController.list() returns 200 for ADMIN
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // POST /api/documents without file body returns 400 Bad Request for ADMIN (security passes, validation fails)
        mvc.perform(post("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        mvc.perform(get("/api/admin/logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void limitsChatPerAuthenticatedUserWithoutLimitingOtherRoutes() throws Exception {
        when(aiService.answer(anyString())).thenReturn(
                new AiAnswer("Không tìm thấy trong quy chế đã nạp.", "no_context", java.util.List.of()));
        String first = register().get("accessToken").asText();
        String second = register().get("accessToken").asText();
        String request = json.writeValueAsString(Map.of("question", "Điều kiện tốt nghiệp?"));

        mvc.perform(post("/api/chat")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mvc.perform(get("/api/chat").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
    void loadsLlmKeyFromEnvironment() {
        assertThat(llmCredentials.requireApiKey()).isEqualTo(System.getenv("LLM_API_KEY"));
    }

    private JsonNode register() throws Exception {
        String email = UUID.randomUUID() + "@iuh.edu.vn";
        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", "integration-password-123"))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/admin/logs", "/api/chat"})
        String read() {
            return "ok";
        }
    }
}
