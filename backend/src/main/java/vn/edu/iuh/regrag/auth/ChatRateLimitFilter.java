package vn.edu.iuh.regrag.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.iuh.regrag.common.ErrorResponse;

/** Limits authenticated POST /api/chat calls per user in a process-local minute window. */
public class ChatRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final int requestsPerMinute;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ChatRateLimitFilter(int requestsPerMinute) {
        this(requestsPerMinute, new ObjectMapper().findAndRegisterModules());
    }

    public ChatRateLimitFilter(int requestsPerMinute, ObjectMapper json) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("CHAT_RATE_LIMIT_PER_MINUTE must be positive");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.json = json != null ? json : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod())
                || (!"/api/chat".equals(path)
                && !"/api/chat/stream".equals(path)
                && !"/api/chat/compare/stream".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.nanoTime();
        Window window = windows.compute(jwt.getSubject(), (userId, previous) -> {
            if (previous == null || now - previous.startedAt() >= WINDOW_NANOS) {
                return new Window(now, 1);
            }
            return new Window(previous.startedAt(), Math.min(requestsPerMinute + 1, previous.count() + 1));
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_NANOS);
        }
        if (window.count() > requestsPerMinute) {
            long remaining = WINDOW_NANOS - (now - window.startedAt());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(Math.max(1,
                    (remaining + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos())));

            ErrorResponse error = ErrorResponse.of(
                    429,
                    "Too Many Requests",
                    "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.",
                    request.getRequestURI());
            response.getWriter().write(json.writeValueAsString(error));
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {
    }
}
