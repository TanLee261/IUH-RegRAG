package vn.edu.iuh.regrag.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "model_comparisons")
public class ModelComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_message_id", nullable = false)
    private Message userMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_message_id")
    private Message assistantMessage;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "model_a", nullable = false, length = 100)
    private String modelA;

    @Column(name = "model_b", nullable = false, length = 100)
    private String modelB;

    @Column(name = "answer_a", nullable = false, columnDefinition = "text")
    private String answerA;

    @Column(name = "answer_b", nullable = false, columnDefinition = "text")
    private String answerB;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_a", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> sourcesA = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_b", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> sourcesB = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CompareWinner winner;

    @Column(name = "voted_at")
    private Instant votedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModelComparison() {
        // Required by JPA.
    }

    public ModelComparison(
            ChatSession session,
            Message userMessage,
            Message assistantMessage,
            UUID userId,
            String question,
            String modelA,
            String modelB,
            String answerA,
            String answerB,
            List<Map<String, Object>> sourcesA,
            List<Map<String, Object>> sourcesB) {
        this.session = Objects.requireNonNull(session);
        this.userMessage = Objects.requireNonNull(userMessage);
        this.assistantMessage = assistantMessage;
        this.userId = userId;
        this.question = Objects.requireNonNull(question);
        this.modelA = Objects.requireNonNull(modelA);
        this.modelB = Objects.requireNonNull(modelB);
        this.answerA = Objects.requireNonNull(answerA);
        this.answerB = Objects.requireNonNull(answerB);
        this.sourcesA = sourcesA != null ? new ArrayList<>(sourcesA) : new ArrayList<>();
        this.sourcesB = sourcesB != null ? new ArrayList<>(sourcesB) : new ArrayList<>();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatSession getSession() {
        return session;
    }

    public Message getUserMessage() {
        return userMessage;
    }

    public Message getAssistantMessage() {
        return assistantMessage;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public String getModelA() {
        return modelA;
    }

    public String getModelB() {
        return modelB;
    }

    public String getAnswerA() {
        return answerA;
    }

    public String getAnswerB() {
        return answerB;
    }

    public List<Map<String, Object>> getSourcesA() {
        return sourcesA;
    }

    public List<Map<String, Object>> getSourcesB() {
        return sourcesB;
    }

    public CompareWinner getWinner() {
        return winner;
    }

    public Instant getVotedAt() {
        return votedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void recordVote(CompareWinner winner) {
        this.winner = Objects.requireNonNull(winner);
        this.votedAt = Instant.now();
    }
}
