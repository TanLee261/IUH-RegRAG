package vn.edu.iuh.regrag.chat;

import java.util.function.Consumer;

public interface AiServiceClient {

    AiAnswer answer(String question);

    boolean isHealthy();

    default void streamAnswer(String question, Consumer<AiStreamEvent> onEvent) {
        AiAnswer full = answer(question);
        onEvent.accept(AiStreamEvent.token(full.answer()));
        onEvent.accept(AiStreamEvent.done(full));
    }

    default void compareStreamAnswer(String question, Consumer<CompareStreamEvent> onEvent) {
        AiAnswer full = answer(question);
        onEvent.accept(CompareStreamEvent.token("A", full.answer()));
        onEvent.accept(CompareStreamEvent.done("A", "default-a", "default", full));
        onEvent.accept(CompareStreamEvent.token("B", full.answer()));
        onEvent.accept(CompareStreamEvent.done("B", "default-b", "default", full));
    }

    record AiStreamEvent(String type, String content, AiAnswer answer, String error) {
        public static AiStreamEvent token(String content) {
            return new AiStreamEvent("token", content, null, null);
        }

        public static AiStreamEvent done(AiAnswer answer) {
            return new AiStreamEvent("done", null, answer, null);
        }

        public static AiStreamEvent error(String error) {
            return new AiStreamEvent("error", null, null, error);
        }
    }

    record CompareStreamEvent(
            String type,
            String slot,
            String content,
            String model,
            String provider,
            AiAnswer answer,
            String error) {
        public static CompareStreamEvent token(String slot, String content) {
            return new CompareStreamEvent("token", slot, content, null, null, null, null);
        }

        public static CompareStreamEvent done(String slot, String model, String provider, AiAnswer answer) {
            return new CompareStreamEvent("done", slot, null, model, provider, answer, null);
        }

        public static CompareStreamEvent error(String slot, String model, String error) {
            return new CompareStreamEvent("error", slot, null, model, null, null, error);
        }
    }
}
