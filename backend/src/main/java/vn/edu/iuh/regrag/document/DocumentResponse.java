package vn.edu.iuh.regrag.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String originalFilename,
        String sourceUrl,
        String storageKey,
        String version,
        String contentSha256,
        String knowledgeDocumentId,
        IngestStatus ingestStatus,
        String ingestError,
        UUID uploadedById,
        Instant createdAt,
        Instant updatedAt) {

    static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getSourceUrl(),
                doc.getStorageKey(),
                doc.getVersion(),
                doc.getContentSha256(),
                doc.getKnowledgeDocumentId(),
                doc.getIngestStatus(),
                doc.getIngestError(),
                doc.getUploadedBy() != null ? doc.getUploadedBy().getId() : null,
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }
}
