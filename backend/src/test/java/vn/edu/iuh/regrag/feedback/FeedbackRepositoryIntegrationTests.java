package vn.edu.iuh.regrag.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.chat.ChatSession;
import vn.edu.iuh.regrag.chat.ChatSessionRepository;
import vn.edu.iuh.regrag.chat.Message;
import vn.edu.iuh.regrag.chat.MessageRepository;
import vn.edu.iuh.regrag.chat.MessageRole;

@SpringBootTest
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FeedbackRepositoryIntegrationTests {

    @Autowired
    private ChatSessionRepository sessions;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private FeedbackRepository feedback;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void onlyAssistantMessagesCanReceiveOneUpdatableFeedback() {
        ChatSession session = sessions.saveAndFlush(new ChatSession(null, "Feedback test"));
        Message question = messages.saveAndFlush(new Message(session, MessageRole.USER, "Dieu 12?"));
        Message answer = messages.saveAndFlush(new Message(session, MessageRole.ASSISTANT, "Quy che dao tao"));

        assertThatThrownBy(() -> new Feedback(question, FeedbackRating.HELPFUL, null))
                .isInstanceOf(IllegalArgumentException.class);

        Feedback saved = feedback.saveAndFlush(new Feedback(answer, FeedbackRating.HELPFUL, "Co ich"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        entityManager.clear();

        Feedback loaded = feedback.findByMessage_Id(answer.getId()).orElseThrow();
        assertThat(loaded.getRating()).isEqualTo(FeedbackRating.HELPFUL);
        assertThat(loaded.getComment()).isEqualTo("Co ich");
        assertThat(loaded.getMessage().getId()).isEqualTo(answer.getId());
        loaded.setRating(FeedbackRating.NOT_HELPFUL);
        loaded.setComment("Can bo sung nguon");
        feedback.flush();
        entityManager.clear();

        Feedback updated = feedback.findByMessage_Id(answer.getId()).orElseThrow();
        assertThat(updated.getRating()).isEqualTo(FeedbackRating.NOT_HELPFUL);
        assertThat(updated.getComment()).isEqualTo("Can bo sung nguon");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
        assertThat(feedback.findByMessage_Id(question.getId())).isEmpty();
    }
}
