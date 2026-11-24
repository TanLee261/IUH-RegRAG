package vn.edu.iuh.regrag.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Document> findByUploadedBy_IdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    Page<Document> findByIngestStatusOrderByCreatedAtDescIdDesc(IngestStatus status, Pageable pageable);

    List<Document> findByKnowledgeDocumentId(String knowledgeDocumentId);
}
