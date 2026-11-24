package vn.edu.iuh.regrag.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.iuh.regrag.common.ErrorResponse;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(JwtDecoder decoder, JdbcTemplate jdbc, UserRepository users) {
        this(decoder, jdbc, users, new ObjectMapper().findAndRegisterModules());
    }

    public JwtAuthenticationFilter(JwtDecoder decoder, JdbcTemplate jdbc, UserRepository users, ObjectMapper json) {
        this.decoder = decoder;
        this.jdbc = jdbc;
        this.users = users;
        this.json = json != null ? json : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            sendUnauthorized(request, response, "Token xác thực không đúng định dạng Bearer.");
            return;
        }
        Jwt jwt;
        User user;
        try {
            jwt = decoder.decode(authorization.substring(7));
            if (!"access".equals(jwt.getClaimAsString("type"))) {
                throw new IllegalArgumentException("not an access token");
            }
            UUID userId = UUID.fromString(jwt.getSubject());
            UUID tokenId = UUID.fromString(jwt.getId());
            Boolean revoked = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM auth_revoked_access_tokens WHERE id = ?)",
                    Boolean.class, tokenId);
            user = users.findById(userId).orElseThrow();
            if (Boolean.TRUE.equals(revoked)) {
                throw new IllegalArgumentException("access token revoked");
            }
        } catch (JwtException | IllegalArgumentException | java.util.NoSuchElementException exception) {
            SecurityContextHolder.clearContext();
            sendUnauthorized(request, response, "Token xác thực không hợp lệ, đã bị thu hồi hoặc đã hết hạn.");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                jwt, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(401, "Unauthorized", message, request.getRequestURI());
        response.getWriter().write(json.writeValueAsString(body));
    }
}
