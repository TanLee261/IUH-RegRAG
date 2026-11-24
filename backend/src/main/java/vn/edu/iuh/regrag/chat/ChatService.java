package vn.edu.iuh.regrag.chat;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
public class ChatService {

    private final AiServiceClient ai;
    private final ChatConversationStore conversations;

    public ChatService(AiServiceClient ai, ChatConversationStore conversations) {
        this.ai = ai;
        this.conversations = conversations;
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
