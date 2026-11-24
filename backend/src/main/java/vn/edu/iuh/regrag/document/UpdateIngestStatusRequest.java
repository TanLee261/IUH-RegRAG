package vn.edu.iuh.regrag.document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateIngestStatusRequest(
        @NotNull(message = "status is required")
        IngestStatus status,

        @Pattern(regexp = "^[0-9a-f]{64}$",
                 message = "knowledgeDocumentId must be exactly 64 lowercase hex characters")
        String knowledgeDocumentId,

        String ingestError) {
}
