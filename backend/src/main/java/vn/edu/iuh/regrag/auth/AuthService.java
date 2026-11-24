package vn.edu.iuh.regrag.auth;

import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@Service
@Profile("db")
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public TokenResponse register(String email, String password) {
        String normalized = normalize(email);
        if (normalized.contains("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hệ thống chỉ cho phép duy nhất một tài khoản quản trị.");
        }
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        try {
            User user = users.saveAndFlush(new User(normalized, passwords.encode(password), UserRole.STUDENT));
            return tokens.issue(user.getId());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", exception);
        }
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(normalize(email))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return tokens.issue(user.getId());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        UUID userId = tokens.consumeRefresh(refreshToken);
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return tokens.issue(userId);
    }

    @Transactional
    public void logout(Jwt access, String refreshToken) {
        tokens.revoke(refreshToken, access);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
