package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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

    @Test
    void streamsTokensAndPersistsFinalAnswer() {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatConversationStore store = mock(ChatConversationStore.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        AiAnswer answer = new AiAnswer("Trả lời stream", "answered", List.of());

        doAnswer(invocation -> {
            Consumer<AiServiceClient.AiStreamEvent> consumer = invocation.getArgument(1);
            consumer.accept(AiServiceClient.AiStreamEvent.token("Trả lời "));
            consumer.accept(AiServiceClient.AiStreamEvent.token("stream"));
            consumer.accept(AiServiceClient.AiStreamEvent.done(answer));
            return null;
        }).when(ai).streamAnswer(eq("Điều kiện tốt nghiệp?"), any());

        when(store.persist(userId, sessionId, "Điều kiện tốt nghiệp?", answer))
                .thenReturn(new ChatConversationStore.StoredExchange(
                        sessionId, userMessageId, assistantMessageId));

        List<ChatService.ChatStreamEvent> events = new ArrayList<>();
        new ChatService(ai, store).streamAnswer(userId, sessionId, "  Điều kiện tốt nghiệp?  ", events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).type()).isEqualTo("token");
        assertThat(events.get(0).content()).isEqualTo("Trả lời ");
        assertThat(events.get(1).content()).isEqualTo("stream");
        assertThat(events.get(2).type()).isEqualTo("done");
        assertThat(events.get(2).response().assistantMessageId()).isEqualTo(assistantMessageId);
        assertThat(events.get(2).response().answer()).isEqualTo("Trả lời stream");
    }

    @Test
    void streamsCompareAnswersAndPersistsBothWithoutLeakingModelNamesBeforeVote() {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatConversationStore store = mock(ChatConversationStore.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID comparisonId = UUID.randomUUID();
        UUID userMsgId = UUID.randomUUID();
        UUID assistantMsgId = UUID.randomUUID();

        AiAnswer answerA = new AiAnswer("Trả lời A", "answered", List.of());
        AiAnswer answerB = new AiAnswer("Trả lời B", "answered", List.of());

        doAnswer(invocation -> {
            Consumer<AiServiceClient.CompareStreamEvent> consumer = invocation.getArgument(1);
            consumer.accept(AiServiceClient.CompareStreamEvent.token("A", "Trả lời A"));
            consumer.accept(AiServiceClient.CompareStreamEvent.token("B", "Trả lời B"));
            consumer.accept(AiServiceClient.CompareStreamEvent.done("A", "qwen2.5:3b", "ollama", answerA));
            consumer.accept(AiServiceClient.CompareStreamEvent.done("B", "gemma3:4b", "ollama", answerB));
            return null;
        }).when(ai).compareStreamAnswer(eq("Điều kiện tốt nghiệp?"), any());

        when(store.persistComparison(userId, sessionId, "Điều kiện tốt nghiệp?", "qwen2.5:3b", "gemma3:4b", answerA, answerB))
                .thenReturn(new ChatConversationStore.StoredComparison(comparisonId, sessionId, userMsgId, assistantMsgId));

        List<ChatService.CompareUiStreamEvent> events = new ArrayList<>();
        new ChatService(ai, store).compareStreamAnswer(userId, sessionId, "Điều kiện tốt nghiệp?", events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).slot()).isEqualTo("A");
        assertThat(events.get(1).slot()).isEqualTo("B");
        assertThat(events.get(2).type()).isEqualTo("compare_done");
        assertThat(events.get(2).done().comparisonId()).isEqualTo(comparisonId);
        assertThat(events.get(2).done().answerA()).isEqualTo("Trả lời A");
        assertThat(events.get(2).done().answerB()).isEqualTo("Trả lời B");
    }
}
