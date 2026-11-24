package vn.edu.iuh.regrag.chat;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID sessionId,
        UUID userMessageId,
        UUID assistantMessageId,
        String answer,
        String status,
        List<AiAnswer.Source> sources) {
}
