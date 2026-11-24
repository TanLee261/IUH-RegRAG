package vn.edu.iuh.regrag.system;

import java.time.Instant;

public record ActiveDocumentResponse(
        String id,
        String title,
        String source,
        String documentGroup,
        String soQd,
        Integer publicationYear,
        String legalStatus,
        long chunkCount,
        Instant updatedAt) {
}
