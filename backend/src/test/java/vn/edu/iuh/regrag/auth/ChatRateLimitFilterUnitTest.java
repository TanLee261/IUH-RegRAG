package vn.edu.iuh.regrag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class ChatRateLimitFilterUnitTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private ChatRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ChatRateLimitFilter(3, json);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsZeroOrNegativeRateLimitInConstructor() {
        assertThatThrownBy(() -> new ChatRateLimitFilter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> new ChatRateLimitFilter(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldNotFilterNonChatOrNonPostRequests() throws Exception {
        Method shouldNotFilter = ChatRateLimitFilter.class.getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        shouldNotFilter.setAccessible(true);

        MockHttpServletRequest getChat = new MockHttpServletRequest("GET", "/api/chat");
        assertThat((boolean) shouldNotFilter.invoke(filter, getChat)).isTrue();

        MockHttpServletRequest postSessions = new MockHttpServletRequest("POST", "/api/chat/sessions");
        assertThat((boolean) shouldNotFilter.invoke(filter, postSessions)).isTrue();

        MockHttpServletRequest postDocuments = new MockHttpServletRequest("POST", "/api/documents");
        assertThat((boolean) shouldNotFilter.invoke(filter, postDocuments)).isTrue();

        MockHttpServletRequest postChat = new MockHttpServletRequest("POST", "/api/chat");
        assertThat((boolean) shouldNotFilter.invoke(filter, postChat)).isFalse();
    }

    @Test
    void unauthenticatedOrNonJwtRequestsPassThroughWithoutRateLimiting() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);

        // Also test non-Jwt principal
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request, response2, chain);
        verify(chain).doFilter(request, response2);
    }

    @Test
    void allowsRequestsWithinConfiguredRateLimit() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        setJwtAuthentication(userId);

        FilterChain chain = mock(FilterChain.class);

        // Rate limit is 3 requests per minute
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void blocksRequestExceedingRateLimitWith429AndStructuredError() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        setJwtAuthentication(userId);

        FilterChain allowedChain = mock(FilterChain.class);
        FilterChain blockedChain = mock(FilterChain.class);

        // Exhaust the 3 allowed requests
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, allowedChain);
        }

        // 4th request must be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, blockedChain);

        verifyNoInteractions(blockedChain);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getContentType()).contains("application/json");
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();

        JsonNode body = json.readTree(blockedResponse.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("error").asText()).isEqualTo("Too Many Requests");
        assertThat(body.get("message").asText()).contains("quá nhiều yêu cầu");
        assertThat(body.get("path").asText()).isEqualTo("/api/chat");
    }

    @Test
    void isolatesRateLimitsBetweenDifferentUsers() throws ServletException, IOException {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FilterChain chain = mock(FilterChain.class);

        // Exhaust limit for User A
        setJwtAuthentication(userA);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }

        // 4th request for User A is blocked
        MockHttpServletRequest req4A = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse res4A = new MockHttpServletResponse();
        filter.doFilter(req4A, res4A, mock(FilterChain.class));
        assertThat(res4A.getStatus()).isEqualTo(429);

        // User B should still be allowed
        setJwtAuthentication(userB);
        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(reqB, resB, chain);

        verify(chain).doFilter(reqB, resB);
        assertThat(resB.getStatus()).isEqualTo(200);
    }

    private void setJwtAuthentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("mock.token.jwt")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("type", "access")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null));
    }
}
