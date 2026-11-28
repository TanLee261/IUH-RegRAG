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
}
