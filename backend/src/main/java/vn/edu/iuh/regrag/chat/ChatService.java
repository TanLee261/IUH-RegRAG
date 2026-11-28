package vn.edu.iuh.regrag.chat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class ChatService {

    private final AiServiceClient ai;
    private final ChatConversationStore conversations;

    public ChatService(AiServiceClient ai, ChatConversationStore conversations) {
        this.ai = ai;
        this.conversations = conversations;
    }

    public void verifySessionOwnership(UUID userId, UUID sessionId) {
        conversations.assertOwned(sessionId, userId);
    }

    public ChatResponse answer(UUID userId, UUID sessionId, String rawQuestion) {
        String question = rawQuestion.trim();
        conversations.assertOwned(sessionId, userId);
        AiAnswer answer = ai.answer(question);
        ChatConversationStore.StoredExchange stored = conversations.persist(
                userId, sessionId, question, answer);
        return new ChatResponse(
                stored.sessionId(),
                stored.userMessageId(),
                stored.assistantMessageId(),
                answer.answer(),
                answer.status(),
                answer.sources());
    }

    public void streamAnswer(
            UUID userId,
            UUID sessionId,
            String rawQuestion,
            Consumer<ChatStreamEvent> onEvent) {
        String question = rawQuestion.trim();
        conversations.assertOwned(sessionId, userId);

        StringBuilder buffer = new StringBuilder();
        AtomicReference<AiAnswer> completedAnswer = new AtomicReference<>();

        ai.streamAnswer(question, event -> {
            switch (event.type()) {
                case "token" -> {
                    String content = event.content();
                    if (content != null && !content.isEmpty()) {
                        buffer.append(content);
                        onEvent.accept(ChatStreamEvent.token(content));
                    }
                }
                case "done" -> completedAnswer.set(event.answer());
                case "error" -> throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        event.error() != null ? event.error() : "AI service unavailable");
                default -> {
                    // Ignore unknown events
                }
            }
        });

        AiAnswer finalAnswer = completedAnswer.get();
        if (finalAnswer == null) {
            String accumulated = buffer.toString().trim();
            if (accumulated.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "AI service returned an empty stream");
            }
            finalAnswer = new AiAnswer(accumulated, "answered", List.of());
        }

        ChatConversationStore.StoredExchange stored = conversations.persist(
                userId, sessionId, question, finalAnswer);
        ChatResponse response = new ChatResponse(
                stored.sessionId(),
                stored.userMessageId(),
                stored.assistantMessageId(),
                finalAnswer.answer(),
                finalAnswer.status(),
                finalAnswer.sources());
        onEvent.accept(ChatStreamEvent.done(response));
    }

    public record ChatStreamEvent(String type, String content, ChatResponse response) {
        public static ChatStreamEvent token(String content) {
            return new ChatStreamEvent("token", content, null);
        }

        public static ChatStreamEvent done(ChatResponse response) {
            return new ChatStreamEvent("done", null, response);
        }
    }

    public Page<ChatSession> listSessions(UUID userId, Pageable pageable) {
        return conversations.listSessions(userId, pageable);
    }

    public ChatSession getSession(UUID sessionId, UUID userId) {
        return conversations.getSession(sessionId, userId);
    }

    public Page<Message> listMessages(UUID sessionId, UUID userId, Pageable pageable) {
        return conversations.listMessages(sessionId, userId, pageable);
    }
}
