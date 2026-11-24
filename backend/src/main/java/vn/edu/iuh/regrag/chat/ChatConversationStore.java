package vn.edu.iuh.regrag.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
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

    public ChatConversationStore(UserRepository users, ChatSessionRepository sessions,
                                 MessageRepository messages) {
        this.users = users;
        this.sessions = sessions;
        this.messages = messages;
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

    public record StoredExchange(UUID sessionId, UUID userMessageId, UUID assistantMessageId) {
    }
}
