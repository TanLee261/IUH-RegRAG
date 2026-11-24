package vn.edu.iuh.regrag.feedback;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID messageId,
        FeedbackRating rating,
        String comment,
        Instant createdAt,
        Instant updatedAt) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getMessage().getId(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }
}
