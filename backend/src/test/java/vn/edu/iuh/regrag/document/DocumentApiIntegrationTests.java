package vn.edu.iuh.regrag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.chat.AiServiceClient;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest(properties = "app.security.chat-requests-per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class DocumentApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private AiServiceClient aiService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private String register() throws Exception {
        JsonNode response = response(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", UUID.randomUUID() + "@iuh.edu.vn",
                        "password", "integration-password-123"))), 201);
        return response.get("accessToken").asText();
    }

    private String registerAdmin() throws Exception {
        String token = register();
        UUID userId = UUID.fromString(decodeSubject(token));
        var user = users.findById(userId).orElseThrow();
        user.setRole(UserRole.ADMIN);
        users.saveAndFlush(user);
        return token;
    }

    private String decodeSubject(String token) throws Exception {
        // JWT subject is in the payload (base64url part 2)
        String payload = token.split("\\.")[1];
        int padding = (4 - payload.length() % 4) % 4;
        payload = payload + "=".repeat(padding);
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        JsonNode node = json.readTree(new String(decoded, StandardCharsets.UTF_8));
        return node.get("sub").asText();
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile(
                "file", "quy-che.txt",
                "text/plain",
                "Điều 1. Phạm vi áp dụng...".getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int statusCode) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    // ── RBAC security tests ──────────────────────────────────────────────────

    @Test
    @Transactional
    void unauthenticatedCannotAccessDocumentEndpoints() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/documents/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void studentIsForbidenFromDocumentEndpoints() throws Exception {
        String studentToken = register();
        mvc.perform(get("/api/documents")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/documents/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/documents/" + UUID.randomUUID() + "/status")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isForbidden());
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void adminCanUploadDocumentAndStatusIsPending() throws Exception {
        String adminToken = registerAdmin();

        var result = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Quy chế đào tạo 2025")
                        .param("version", "QD 2025")
                        .param("sourceUrl", "https://pdt.iuh.edu.vn/quy-che")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode doc = json.readTree(result.getResponse().getContentAsString());
        assertThat(doc.get("id").asText()).isNotBlank();
        assertThat(doc.get("title").asText()).isEqualTo("Quy chế đào tạo 2025");
        assertThat(doc.get("version").asText()).isEqualTo("QD 2025");
        assertThat(doc.get("sourceUrl").asText()).isEqualTo("https://pdt.iuh.edu.vn/quy-che");
        assertThat(doc.get("ingestStatus").asText()).isEqualTo("PENDING");
        assertThat(doc.get("contentSha256").asText()).matches("[0-9a-f]{64}");
        assertThat(doc.get("storageKey").asText()).isNotBlank();
        assertThat(doc.get("originalFilename").asText()).isEqualTo("quy_che.txt");
    }

    @Test
    @Transactional
    void uploadRequiresTitleParam() throws Exception {
        String adminToken = registerAdmin();
        mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void uploadRequiresNonEmptyFile() throws Exception {
        String adminToken = registerAdmin();
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/api/documents")
                        .file(emptyFile)
                        .param("title", "Tài liệu rỗng")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void adminCanListAllDocumentsWithPagination() throws Exception {
        String adminToken = registerAdmin();

        // Upload 3 documents
        for (int i = 1; i <= 3; i++) {
            mvc.perform(multipart("/api/documents")
                            .file(sampleFile())
                            .param("title", "Tài liệu " + i)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isCreated());
        }

        JsonNode page = response(get("/api/documents")
                .param("page", "0").param("size", "2")
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(page.get("content")).hasSize(2);
    }

    @Test
    @Transactional
    void adminCanFilterDocumentsByIngestStatus() throws Exception {
        String adminToken = registerAdmin();
        mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu lọc status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        JsonNode pending = response(get("/api/documents")
                .param("status", "PENDING")
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(pending.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        // Mỗi phần tử phải có status PENDING
        for (JsonNode item : pending.get("content")) {
            assertThat(item.get("ingestStatus").asText()).isEqualTo("PENDING");
        }
    }

    // ── Get Detail ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void adminCanGetDocumentDetailById() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Chi tiết tài liệu")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        JsonNode detail = response(get("/api/documents/" + docId)
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(detail.get("id").asText()).isEqualTo(docId);
        assertThat(detail.get("title").asText()).isEqualTo("Chi tiết tài liệu");
    }

    @Test
    @Transactional
    void getDocumentReturns404WhenNotFound() throws Exception {
        String adminToken = registerAdmin();
        mvc.perform(get("/api/documents/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void adminCanDeleteDocument() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu cần xóa")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(delete("/api/documents/" + docId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify: GET should return 404 after delete
        mvc.perform(get("/api/documents/" + docId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── Update Ingest Status ─────────────────────────────────────────────────

    @Test
    @Transactional
    void adminCanUpdateIngestStatusToProcessing() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu cập nhật status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        JsonNode updated = response(patch("/api/documents/" + docId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "PROCESSING"))), 200);
        assertThat(updated.get("ingestStatus").asText()).isEqualTo("PROCESSING");
    }

    @Test
    @Transactional
    void updateStatusToReadyRequiresKnowledgeDocumentId() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu READY thiếu ID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Should fail: READY without knowledgeDocumentId
        mvc.perform(patch("/api/documents/" + docId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "READY"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateStatusToReadyWithValidKnowledgeDocumentId() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu READY có ID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        String validKnowledgeId = "a".repeat(64);
        JsonNode ready = response(patch("/api/documents/" + docId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "status", "READY",
                        "knowledgeDocumentId", validKnowledgeId))), 200);
        assertThat(ready.get("ingestStatus").asText()).isEqualTo("READY");
        assertThat(ready.get("knowledgeDocumentId").asText()).isEqualTo(validKnowledgeId);
    }

    @Test
    @Transactional
    void updateStatusWithInvalidKnowledgeDocumentIdReturns400() throws Exception {
        String adminToken = registerAdmin();
        var createResult = mvc.perform(multipart("/api/documents")
                        .file(sampleFile())
                        .param("title", "Tài liệu ID sai")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        String docId = json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/documents/" + docId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "status", "READY",
                        "knowledgeDocumentId", "invalid-id"))))
                .andExpect(status().isBadRequest());
    }
}
