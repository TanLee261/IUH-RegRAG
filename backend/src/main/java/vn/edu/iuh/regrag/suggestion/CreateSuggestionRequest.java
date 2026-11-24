package vn.edu.iuh.regrag.suggestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
        @NotBlank(message = "topic is required")
        @Size(max = 100, message = "topic must not exceed 100 characters")
        String topic,

        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must not exceed 1000 characters")
        String question,

        Integer displayOrder,

        Boolean active) {
}
