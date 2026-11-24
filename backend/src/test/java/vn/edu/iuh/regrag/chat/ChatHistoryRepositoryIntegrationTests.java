package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ChatHistoryRepositoryIntegrationTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private ChatSessionRepository sessions;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsOrderedHistoryAndCitationSnapshot() {
        User student = users.saveAndFlush(new User("chat-test@iuh.edu.vn", "test-hash", UserRole.STUDENT));
        ChatSession session = sessions.saveAndFlush(new ChatSession(student, "Quy che hoc vu"));
        ChatSession anonymous = sessions.saveAndFlush(new ChatSession(null, "Anonymous"));
        Message question = messages.saveAndFlush(new Message(session, MessageRole.USER, "Dieu 12?"));
        Map<String, Object> citation = new HashMap<>(Map.of(
                "title", "Quy che dao tao", "dieu", "12",
                "source", "https://pdt.iuh.edu.vn/quy-che-dao-tao"));
        citation.put("khoan", null);
        Message answer = messages.saveAndFlush(new Message(
                session, MessageRole.ASSISTANT, "Canh bao hoc tap", List.of(citation)));
        session.markActivity();
        sessions.flush();

        entityManager.clear();

        List<ChatSession> userSessions = sessions.findByUser_IdOrderByUpdatedAtDescIdDesc(
                student.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(userSessions).extracting(ChatSession::getId).containsExactly(session.getId());
        assertThat(userSessions).extracting(ChatSession::getId).doesNotContain(anonymous.getId());
        assertThat(userSessions.get(0).getUpdatedAt()).isAfterOrEqualTo(session.getCreatedAt());
        List<Message> history = messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                session.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(history).extracting(Message::getId).containsExactly(question.getId(), answer.getId());
        assertThat(history.get(0).getCitations()).isEmpty();
        assertThat(history.get(1).getCitations()).containsExactly(citation);
        assertThat(history.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThatThrownBy(() -> new Message(session, MessageRole.USER, "Not allowed", List.of(citation)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
