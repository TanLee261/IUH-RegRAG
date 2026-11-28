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

    public void compareStreamAnswer(
            UUID userId,
            UUID sessionId,
            String rawQuestion,
            Consumer<CompareUiStreamEvent> onEvent) {
        String question = rawQuestion.trim();
        conversations.assertOwned(sessionId, userId);

        StringBuilder bufferA = new StringBuilder();
        StringBuilder bufferB = new StringBuilder();
        AtomicReference<String> modelA = new AtomicReference<>("model-a");
        AtomicReference<String> modelB = new AtomicReference<>("model-b");
        AtomicReference<AiAnswer> doneA = new AtomicReference<>();
        AtomicReference<AiAnswer> doneB = new AtomicReference<>();

        ai.compareStreamAnswer(question, event -> {
            String slot = event.slot();
            switch (event.type()) {
                case "token" -> {
                    String content = event.content();
                    if (content == null || content.isEmpty()) {
                        return;
                    }
                    if ("A".equals(slot)) {
                        bufferA.append(content);
                    } else if ("B".equals(slot)) {
                        bufferB.append(content);
                    }
                    onEvent.accept(CompareUiStreamEvent.token(slot, content));
                }
                case "done" -> {
                    if ("A".equals(slot)) {
                        if (event.model() != null && !event.model().isBlank()) {
                            modelA.set(event.model());
                        }
                        doneA.set(event.answer());
                    } else if ("B".equals(slot)) {
                        if (event.model() != null && !event.model().isBlank()) {
                            modelB.set(event.model());
                        }
                        doneB.set(event.answer());
                    }
                }
                case "error" -> {
                    String fallbackErr = event.error() != null
                            ? event.error()
                            : "Model gặp lỗi khi sinh câu trả lời.";
                    if ("A".equals(slot)) {
                        if (event.model() != null && !event.model().isBlank()) {
                            modelA.set(event.model());
                        }
                        doneA.set(new AiAnswer(fallbackErr, "error", List.of()));
                    } else if ("B".equals(slot)) {
                        if (event.model() != null && !event.model().isBlank()) {
                            modelB.set(event.model());
                        }
                        doneB.set(new AiAnswer(fallbackErr, "error", List.of()));
                    }
                    onEvent.accept(CompareUiStreamEvent.slotError(slot, fallbackErr));
                }
                default -> {
                    // Ignore unknown events
                }
            }
        });

        AiAnswer finalA = resolveCompareAnswer(doneA.get(), bufferA);
        AiAnswer finalB = resolveCompareAnswer(doneB.get(), bufferB);

        ChatConversationStore.StoredComparison stored = conversations.persistComparison(
                userId,
                sessionId,
                question,
                modelA.get(),
                modelB.get(),
                finalA,
                finalB);

        onEvent.accept(CompareUiStreamEvent.done(new CompareDonePayload(
                stored.comparisonId(),
                stored.sessionId(),
                stored.userMessageId(),
                stored.assistantMessageId(),
                finalA.answer(),
                finalB.answer(),
                finalA.sources(),
                finalB.sources())));
    }

    public VoteResponse voteComparison(UUID userId, UUID comparisonId, CompareWinner winner) {
        ModelComparison updated = conversations.voteComparison(userId, comparisonId, winner);
        return new VoteResponse(
                updated.getId(),
                updated.getWinner(),
                updated.getModelA(),
                updated.getModelB());
    }

    private static AiAnswer resolveCompareAnswer(AiAnswer completed, StringBuilder buffer) {
        if (completed != null) {
            return completed;
        }
        String text = buffer.toString().trim();
        if (text.isEmpty()) {
            return new AiAnswer("Không nhận được phản hồi từ mô hình.", "error", List.of());
        }
        return new AiAnswer(text, "answered", List.of());
    }

    public record CompareDonePayload(
            UUID comparisonId,
            UUID sessionId,
            UUID userMessageId,
            UUID assistantMessageId,
            String answerA,
            String answerB,
            List<AiAnswer.Source> sourcesA,
            List<AiAnswer.Source> sourcesB) {
    }

    public record CompareUiStreamEvent(
            String type,
            String slot,
            String content,
            String error,
            CompareDonePayload done) {
        public static CompareUiStreamEvent token(String slot, String content) {
            return new CompareUiStreamEvent("token", slot, content, null, null);
        }

        public static CompareUiStreamEvent slotError(String slot, String error) {
            return new CompareUiStreamEvent("slot_error", slot, null, error, null);
        }

        public static CompareUiStreamEvent done(CompareDonePayload done) {
            return new CompareUiStreamEvent("compare_done", null, null, null, done);
        }
    }

    public record VoteResponse(
            UUID comparisonId,
            CompareWinner winner,
            String modelA,
            String modelB) {
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
