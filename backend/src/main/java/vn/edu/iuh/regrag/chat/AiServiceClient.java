package vn.edu.iuh.regrag.chat;

public interface AiServiceClient {

    AiAnswer answer(String question);

    boolean isHealthy();
}
