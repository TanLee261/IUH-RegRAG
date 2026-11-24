package vn.edu.iuh.regrag.suggestion;

import java.time.Instant;
import java.util.UUID;

public record SuggestionResponse(
        UUID id,
        String topic,
        String question,
        int displayOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SuggestionResponse from(Suggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getTopic(),
                suggestion.getQuestion(),
                suggestion.getDisplayOrder(),
                suggestion.isActive(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt());
    }
}
