package vn.edu.iuh.regrag.feedback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FeedbackRequest(
        @NotNull(message = "messageId is required")
        UUID messageId,

        @NotNull(message = "rating is required")
        FeedbackRating rating,

        @Size(max = 2000, message = "comment must not exceed 2000 characters")
        String comment) {
}
