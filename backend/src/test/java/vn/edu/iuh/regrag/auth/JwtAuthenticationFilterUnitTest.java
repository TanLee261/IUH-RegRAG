package vn.edu.iuh.regrag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

class JwtAuthenticationFilterUnitTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private JwtDecoder decoder;
    private JdbcTemplate jdbc;
    private UserRepository users;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        decoder = mock(JwtDecoder.class);
        jdbc = mock(JdbcTemplate.class);
        users = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(decoder, jdbc, users, json);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWhenNoAuthorizationHeaderPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMalformedAuthorizationHeaderFormat() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);

        // Non-Bearer header
        MockHttpServletRequest basicRequest = new MockHttpServletRequest("GET", "/api/chat");
        basicRequest.addHeader("Authorization", "Basic username:password");
        MockHttpServletResponse basicResponse = new MockHttpServletResponse();

        filter.doFilter(basicRequest, basicResponse, chain);

        verifyNoInteractions(chain);
        assertThat(basicResponse.getStatus()).isEqualTo(401);
        JsonNode basicBody = json.readTree(basicResponse.getContentAsString());
        assertThat(basicBody.get("message").asText()).contains("Bearer");

        // "Bearer " without token
        MockHttpServletRequest emptyBearerRequest = new MockHttpServletRequest("GET", "/api/chat");
        emptyBearerRequest.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse emptyBearerResponse = new MockHttpServletResponse();

        filter.doFilter(emptyBearerRequest, emptyBearerResponse, chain);
        assertThat(emptyBearerResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsInvalidOrExpiredJwtFromDecoder() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        request.addHeader("Authorization", "Bearer invalid.expired.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(decoder.decode("invalid.expired.token")).thenThrow(new BadJwtException("Token has expired"));

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("message").asText()).contains("Token xác thực không hợp lệ");
    }

    @Test
    void rejectsNonAccessTokenTypeClaim() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        request.addHeader("Authorization", "Bearer refresh.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Jwt refreshJwt = Jwt.withTokenValue("refresh.jwt.token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .jti(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .build();
        when(decoder.decode("refresh.jwt.token")).thenReturn(refreshJwt);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsRevokedAccessTokenInDatabase() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        request.addHeader("Authorization", "Bearer revoked.token.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Jwt jwt = Jwt.withTokenValue("revoked.token.jwt")
                .header("alg", "none")
                .subject(userId.toString())
                .jti(tokenId.toString())
                .claim("type", "access")
                .build();
        when(decoder.decode("revoked.token.jwt")).thenReturn(jwt);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(tokenId))).thenReturn(Boolean.TRUE);

        User mockUser = new User("student@iuh.edu.vn", "hash12345", UserRole.STUDENT);
        ReflectionTestUtils.setField(mockUser, "id", userId);
        when(users.findById(userId)).thenReturn(Optional.of(mockUser));

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsWhenUserNotFoundInDatabase() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        request.addHeader("Authorization", "Bearer valid.token.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Jwt jwt = Jwt.withTokenValue("valid.token.jwt")
                .header("alg", "none")
                .subject(userId.toString())
                .jti(tokenId.toString())
                .claim("type", "access")
                .build();
        when(decoder.decode("valid.token.jwt")).thenReturn(jwt);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(tokenId))).thenReturn(Boolean.FALSE);
        when(users.findById(userId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void successfullyAuthenticatesValidStudentAndSetsGrantedAuthority() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        request.addHeader("Authorization", "Bearer valid.token.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Jwt jwt = Jwt.withTokenValue("valid.token.jwt")
                .header("alg", "none")
                .subject(userId.toString())
                .jti(tokenId.toString())
                .claim("type", "access")
                .build();
        when(decoder.decode("valid.token.jwt")).thenReturn(jwt);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(tokenId))).thenReturn(Boolean.FALSE);

        User user = new User("student@iuh.edu.vn", "hash12345", UserRole.STUDENT);
        ReflectionTestUtils.setField(user, "id", userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(jwt);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
    }

    @Test
    void successfullyAuthenticatesAdminUserWithRoleAdminAuthority() throws ServletException, IOException {
        UUID adminId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        request.addHeader("Authorization", "Bearer valid.admin.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Jwt jwt = Jwt.withTokenValue("valid.admin.token")
                .header("alg", "none")
                .subject(adminId.toString())
                .jti(tokenId.toString())
                .claim("type", "access")
                .build();
        when(decoder.decode("valid.admin.token")).thenReturn(jwt);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(tokenId))).thenReturn(Boolean.FALSE);

        User admin = new User("admin@iuh.edu.vn", "hash12345", UserRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", adminId);
        when(users.findById(adminId)).thenReturn(Optional.of(admin));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }
}
