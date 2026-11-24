package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ChatServiceTests {

    @Test
    void verifiesOwnershipCallsAiAndPersistsTrimmedQuestion() {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatConversationStore store = mock(ChatConversationStore.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        AiAnswer answer = new AiAnswer("Trả lời", "answered", List.of());
        when(ai.answer("Điều 19 là gì?")).thenReturn(answer);
        when(store.persist(userId, sessionId, "Điều 19 là gì?", answer))
                .thenReturn(new ChatConversationStore.StoredExchange(
                        sessionId, userMessageId, assistantMessageId));

        ChatResponse response = new ChatService(ai, store).answer(
                userId, sessionId, "  Điều 19 là gì?  ");

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.userMessageId()).isEqualTo(userMessageId);
        assertThat(response.assistantMessageId()).isEqualTo(assistantMessageId);
        assertThat(response.answer()).isEqualTo("Trả lời");
        InOrder order = inOrder(store, ai);
        order.verify(store).assertOwned(sessionId, userId);
        order.verify(ai).answer("Điều 19 là gì?");
        order.verify(store).persist(userId, sessionId, "Điều 19 là gì?", answer);
    }
}
