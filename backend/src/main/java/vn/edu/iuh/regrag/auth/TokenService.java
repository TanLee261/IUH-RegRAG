package vn.edu.iuh.regrag.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class TokenService {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JdbcTemplate jdbc;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, JdbcTemplate jdbc) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.jdbc = jdbc;
    }

    public TokenResponse issue(UUID userId) {
        String role = null;
        String email = null;
        try {
            var row = jdbc.queryForMap("SELECT role, email FROM users WHERE id = ?", userId);
            role = (String) row.get("role");
            email = (String) row.get("email");
        } catch (Exception ignored) {
        }
        Instant now = Instant.now();
        String access = encode(userId, role, email, UUID.randomUUID(), "access", now, now.plus(ACCESS_TTL));
        UUID refreshId = UUID.randomUUID();
        Instant refreshExpires = now.plus(REFRESH_TTL);
        String refresh = encode(userId, role, email, refreshId, "refresh", now, refreshExpires);
        jdbc.update("""
                INSERT INTO auth_refresh_tokens (id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, refreshId, userId, hash(refresh), Timestamp.from(refreshExpires));
        return new TokenResponse(access, refresh, "Bearer", ACCESS_TTL.toSeconds());
    }

    private String encode(UUID userId, String role, String email, UUID tokenId, String type, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("iuh-regrag")
                .subject(userId.toString())
                .id(tokenId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("type", type);
        if (role != null) {
            builder.claim("role", role);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        JwtClaimsSet claims = builder.build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public UUID consumeRefresh(String raw) {
        RefreshToken token = loadRefresh(raw);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at = now() WHERE id = ?", token.id());
        return token.userId();
    }

    public void revoke(String rawRefresh, Jwt access) {
        RefreshToken token = loadRefresh(rawRefresh);
        if (!token.userId().toString().equals(access.getSubject())) {
            throw unauthorized();
        }
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at = now() WHERE id = ?", token.id());
        jdbc.update("""
                INSERT INTO auth_revoked_access_tokens (id, expires_at)
                VALUES (?, ?) ON CONFLICT (id) DO NOTHING
                """, UUID.fromString(access.getId()), Timestamp.from(access.getExpiresAt()));
    }

    private RefreshToken loadRefresh(String raw) {
        try {
            Jwt jwt = decoder.decode(raw);
            if (!"refresh".equals(jwt.getClaimAsString("type"))) {
                throw unauthorized();
            }
            UUID id = UUID.fromString(jwt.getId());
            UUID userId = UUID.fromString(jwt.getSubject());
            RefreshToken stored = jdbc.queryForObject("""
                    SELECT user_id, expires_at, revoked_at
                    FROM auth_refresh_tokens
                    WHERE id = ? AND token_hash = ?
                    FOR UPDATE
                    """, (rs, row) -> new RefreshToken(
                    id, rs.getObject("user_id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at") != null),
                    id, hash(raw));
            if (stored == null || !stored.userId().equals(userId)
                    || stored.revoked() || !stored.expiresAt().isAfter(Instant.now())) {
                throw unauthorized();
            }
            return stored;
        } catch (JwtException | IllegalArgumentException | EmptyResultDataAccessException exception) {
            throw unauthorized();
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    private record RefreshToken(UUID id, UUID userId, Instant expiresAt, boolean revoked) {
    }
}
