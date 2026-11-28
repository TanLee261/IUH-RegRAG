package vn.edu.iuh.regrag.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("db")
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatResponse answer(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChatRequest request) {
        return chat.answer(UUID.fromString(jwt.getSubject()), request.sessionId(), request.question());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamAnswer(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody ChatRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        chat.verifySessionOwnership(userId, request.sessionId());

        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            try {
                chat.streamAnswer(userId, request.sessionId(), request.question(), event -> {
                    try {
                        if ("token".equals(event.type())) {
                            emitter.send(SseEmitter.event().data(
                                    Map.of("type", "token", "content", event.content()),
                                    MediaType.APPLICATION_JSON));
                        } else if ("done".equals(event.type()) && event.response() != null) {
                            ChatResponse r = event.response();
                            Map<String, Object> donePayload = new LinkedHashMap<>();
                            donePayload.put("type", "done");
                            donePayload.put("sessionId", r.sessionId());
                            donePayload.put("userMessageId", r.userMessageId());
                            donePayload.put("assistantMessageId", r.assistantMessageId());
                            donePayload.put("answer", r.answer());
                            donePayload.put("status", r.status());
                            donePayload.put("sources", r.sources());
                            emitter.send(SseEmitter.event().data(donePayload, MediaType.APPLICATION_JSON));
                        }
                    } catch (IOException ioEx) {
                        throw new IllegalStateException("Client disconnected during SSE stream", ioEx);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().data(
                            Map.of(
                                    "type", "error",
                                    "message", "Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau."),
                            MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    // Client already disconnected
                }
                emitter.complete();
            }
        });

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping("/sessions")
    public Page<SessionSummary> listSessions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return chat.listSessions(userId, pageable)
                .map(s -> new SessionSummary(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt()));
    }

    @GetMapping("/sessions/{id}")
    public SessionDetail getSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChatSession session = chat.getSession(id, userId);
        Page<Message> msgPage = chat.listMessages(id, userId, PageRequest.of(page, Math.min(size, 200)));
        List<MessageDto> messageDtos = msgPage.getContent().stream()
                .map(m -> new MessageDto(
                        m.getId(),
                        m.getRole().name(),
                        m.getContent(),
                        m.getCitations(),
                        m.getCreatedAt()))
                .toList();
        return new SessionDetail(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messageDtos,
                msgPage.getNumber(),
                msgPage.getTotalPages(),
                msgPage.getTotalElements());
    }

    // ── inner records ────────────────────────────────────────────────────────

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String question,
            UUID sessionId) {
    }

    public record SessionSummary(
            UUID id,
            String title,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MessageDto(
            UUID id,
            String role,
            String content,
            List<Map<String, Object>> citations,
            Instant createdAt) {
    }

    public record SessionDetail(
            UUID id,
            String title,
            Instant createdAt,
            Instant updatedAt,
            List<MessageDto> messages,
            int page,
            int totalPages,
            long totalElements) {
    }
}
