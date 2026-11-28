package vn.edu.iuh.regrag.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;

@Service
@Profile("db")
public class ChatConversationStore {

    private final UserRepository users;
    private final ChatSessionRepository sessions;
    private final MessageRepository messages;
    private final ModelComparisonRepository comparisons;

    public ChatConversationStore(UserRepository users, ChatSessionRepository sessions,
                                 MessageRepository messages, ModelComparisonRepository comparisons) {
        this.users = users;
        this.sessions = sessions;
        this.messages = messages;
        this.comparisons = comparisons;
    }

    @Transactional(readOnly = true)
    public void assertOwned(UUID sessionId, UUID userId) {
        if (sessionId != null && !sessions.existsByIdAndUser_Id(sessionId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ChatSession> listSessions(UUID userId,
            org.springframework.data.domain.Pageable pageable) {
        return sessions.findByUser_IdOrderByUpdatedAtDescIdDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(UUID sessionId, UUID userId) {
        return sessions.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Message> listMessages(UUID sessionId, UUID userId,
            org.springframework.data.domain.Pageable pageable) {
        getSession(sessionId, userId); // kiểm tra ownership
        return messages.findBySession_IdOrderByCreatedAtAscIdAsc(sessionId, pageable);
    }

    @Transactional
    public StoredExchange persist(UUID userId, UUID sessionId, String question, AiAnswer answer) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ChatSession session;
        if (sessionId == null) {
            session = sessions.save(new ChatSession(user, title(question)));
        } else {
            session = sessions.findByIdAndUser_Id(sessionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Chat session not found"));
        }

        Message userMessage = messages.save(new Message(session, MessageRole.USER, question));
        Message assistantMessage = messages.save(new Message(
                session,
                MessageRole.ASSISTANT,
                answer.answer(),
                citations(answer.sources())));
        session.markActivity();
        sessions.save(session);
        messages.flush();
        return new StoredExchange(session.getId(), userMessage.getId(), assistantMessage.getId());
    }

    @Transactional
    public StoredComparison persistComparison(
            UUID userId,
            UUID sessionId,
            String question,
            String modelA,
            String modelB,
            AiAnswer answerA,
            AiAnswer answerB) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ChatSession session;
        if (sessionId == null) {
            session = sessions.save(new ChatSession(user, title(question)));
        } else {
            session = sessions.findByIdAndUser_Id(sessionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Chat session not found"));
        }

        Message userMessage = messages.save(new Message(session, MessageRole.USER, question));
        Message assistantMessage = messages.save(new Message(
                session,
                MessageRole.ASSISTANT,
                answerA.answer(),
                citations(answerA.sources())));

        ModelComparison comparison = comparisons.save(new ModelComparison(
                session,
                userMessage,
                assistantMessage,
                userId,
                question,
                modelA,
                modelB,
                answerA.answer(),
                answerB.answer(),
                citations(answerA.sources()),
                citations(answerB.sources())));

        session.markActivity();
        sessions.save(session);
        comparisons.flush();
        return new StoredComparison(
                comparison.getId(),
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId());
    }

    @Transactional
    public ModelComparison voteComparison(UUID userId, UUID comparisonId, CompareWinner winner) {
        ModelComparison comparison = comparisons.findById(comparisonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comparison not found"));
        if (comparison.getUserId() == null || !comparison.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comparison not found");
        }
        comparison.recordVote(winner);
        Message assistantMessage = comparison.getAssistantMessage();
        if (assistantMessage != null) {
            if (winner == CompareWinner.B) {
                assistantMessage.setContent(comparison.getAnswerB());
                assistantMessage.setCitations(comparison.getSourcesB());
                messages.save(assistantMessage);
            } else if (winner == CompareWinner.A) {
                assistantMessage.setContent(comparison.getAnswerA());
                assistantMessage.setCitations(comparison.getSourcesA());
                messages.save(assistantMessage);
            }
        }
        return comparisons.save(comparison);
    }

    @Transactional(readOnly = true)
    public AdminComparisonController.ComparisonStatsResponse getComparisonStats() {
        List<ModelComparison> all = comparisons.findAll();
        long total = all.size();
        long voted = 0;

        Map<String, long[]> counts = new LinkedHashMap<>();
        // [0]=wins, [1]=losses, [2]=ties, [3]=bothBad
        for (ModelComparison item : all) {
            counts.putIfAbsent(item.getModelA(), new long[4]);
            counts.putIfAbsent(item.getModelB(), new long[4]);
            CompareWinner winner = item.getWinner();
            if (winner == null) {
                continue;
            }
            voted++;
            long[] statsA = counts.get(item.getModelA());
            long[] statsB = counts.get(item.getModelB());
            switch (winner) {
                case A -> {
                    statsA[0]++;
                    statsB[1]++;
                }
                case B -> {
                    statsB[0]++;
                    statsA[1]++;
                }
                case TIE -> {
                    statsA[2]++;
                    statsB[2]++;
                }
                case BOTH_BAD -> {
                    statsA[3]++;
                    statsB[3]++;
                }
            }
        }

        List<AdminComparisonController.ModelScoreSummary> summaries = counts.entrySet().stream()
                .map(entry -> {
                    long[] c = entry.getValue();
                    long modelVoted = c[0] + c[1] + c[2] + c[3];
                    double winRate = modelVoted == 0 ? 0.0 : (c[0] * 100.0) / modelVoted;
                    return new AdminComparisonController.ModelScoreSummary(
                            entry.getKey(),
                            c[0],
                            c[1],
                            c[2],
                            c[3],
                            modelVoted,
                            Math.round(winRate * 10.0) / 10.0);
                })
                .sorted(Comparator.comparingDouble(
                        AdminComparisonController.ModelScoreSummary::winRate).reversed())
                .toList();

        List<AdminComparisonController.RecentComparisonItem> recent = comparisons
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 30)).stream()
                .map(c -> new AdminComparisonController.RecentComparisonItem(
                        c.getId(),
                        c.getQuestion(),
                        c.getModelA(),
                        c.getModelB(),
                        c.getWinner(),
                        c.getVotedAt(),
                        c.getCreatedAt()))
                .toList();

        return new AdminComparisonController.ComparisonStatsResponse(total, voted, summaries, recent);
    }

    public record StoredExchange(UUID sessionId, UUID userMessageId, UUID assistantMessageId) {
    }

    public record StoredComparison(
            UUID comparisonId,
            UUID sessionId,
            UUID userMessageId,
            UUID assistantMessageId) {
    }

    private static String title(String question) {
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private static List<Map<String, Object>> citations(List<AiAnswer.Source> sources) {
        List<Map<String, Object>> citations = new ArrayList<>();
        for (AiAnswer.Source source : sources) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("chunk_id", source.chunkId());
            value.put("document_id", source.documentId());
            value.put("title", source.title());
            value.put("dieu", source.dieu());
            value.put("khoan", source.khoan());
            value.put("source", source.source());
            value.put("snippet", source.snippet());
            value.put("score", source.score());
            citations.add(value);
        }
        return citations;
    }
}
