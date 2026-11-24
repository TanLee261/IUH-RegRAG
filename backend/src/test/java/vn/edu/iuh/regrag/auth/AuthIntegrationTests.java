package vn.edu.iuh.regrag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("db")
@Import(AuthIntegrationTests.ProtectedEndpoint.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class AuthIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Test
    @Transactional
    void registerLoginRotateRefreshAndRevokeOnLogout() throws Exception {
        String email = UUID.randomUUID() + "@iuh.edu.vn";
        String password = "correct-horse-battery";

        mvc.perform(get("/api/test/protected")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", "short"))))
                .andExpect(status().isBadRequest());

        JsonNode registered = response(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email.toUpperCase(), "password", password))), 201);
        String firstAccess = registered.get("accessToken").asText();
        assertThat(registered.get("refreshToken").asText()).isNotBlank();
        assertThat(registered.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(registered.get("expiresIn").asLong()).isEqualTo(900);
        var saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(saved.getPasswordHash()).isNotEqualTo(password).startsWith("$2");
        mvc.perform(get("/api/test/protected").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_STUDENT"));
        mvc.perform(get("/api/test/protected").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/protected").header("Authorization",
                "Bearer " + registered.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        JsonNode login = response(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email.toUpperCase(), "password", password))), 200);
        String loginRefresh = login.get("refreshToken").asText();
        JsonNode rotated = response(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", loginRefresh))), 200);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", loginRefresh))))
                .andExpect(status().isUnauthorized());

        String rotatedAccess = rotated.get("accessToken").asText();
        String rotatedRefresh = rotated.get("refreshToken").asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + rotatedAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", rotatedRefresh))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/test/protected").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", rotatedRefresh))))
                .andExpect(status().isUnauthorized());
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                              int statusCode) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @RestController
    static class ProtectedEndpoint {

        @GetMapping("/api/test/protected")
        String protectedEndpoint(Authentication authentication) {
            return authentication.getAuthorities().iterator().next().getAuthority();
        }
    }
}
