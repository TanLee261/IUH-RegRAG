package vn.edu.iuh.regrag.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.iuh.regrag.user.User;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;

    @Column(length = 100)
    private String version;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    // A logical reference to knowledge_documents.id, not a foreign key: the
    // Python pipeline replaces the retrieval corpus independently.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "knowledge_document_id", length = 64)
    private String knowledgeDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingest_status", nullable = false, length = 16)
    private IngestStatus ingestStatus = IngestStatus.PENDING;

    @Column(name = "ingest_error", columnDefinition = "text")
    private String ingestError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
        // Required by JPA.
    }

    public Document(User uploadedBy, String title) {
        this.uploadedBy = uploadedBy;
        setTitle(title);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String validateSha256(String value) {
        if (value != null && !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 must be 64 lowercase hex characters");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String value = Objects.requireNonNull(title, "title is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.title = value;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = validateSha256(contentSha256);
    }

    public String getKnowledgeDocumentId() {
        return knowledgeDocumentId;
    }

    public void setKnowledgeDocumentId(String knowledgeDocumentId) {
        String value = validateSha256(knowledgeDocumentId);
        if (value == null && ingestStatus == IngestStatus.READY) {
            throw new IllegalStateException("a ready document requires a knowledge document ID");
        }
        this.knowledgeDocumentId = value;
    }

    public IngestStatus getIngestStatus() {
        return ingestStatus;
    }

    public void setIngestStatus(IngestStatus ingestStatus) {
        IngestStatus value = Objects.requireNonNull(ingestStatus, "ingest status is required");
        if (value == IngestStatus.READY && knowledgeDocumentId == null) {
            throw new IllegalStateException("a ready document requires a knowledge document ID");
        }
        this.ingestStatus = value;
    }

    public String getIngestError() {
        return ingestError;
    }

    public void setIngestError(String ingestError) {
        this.ingestError = ingestError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
