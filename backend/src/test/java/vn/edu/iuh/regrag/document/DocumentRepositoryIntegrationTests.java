package vn.edu.iuh.regrag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;
import vn.edu.iuh.regrag.user.UserRole;

@SpringBootTest
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class DocumentRepositoryIntegrationTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsMetadataAndTracksIngestStatusWithoutAVectorForeignKey() {
        User admin = users.saveAndFlush(new User("document-test@iuh.edu.vn", "test-hash", UserRole.ADMIN));
        Document document = new Document(admin, "Quy che dao tao");
        document.setOriginalFilename("quy-che.pdf");
        document.setSourceUrl("https://pdt.iuh.edu.vn/quy-che-dao-tao");
        document.setStorageKey("uploads/quy-che.pdf");
        document.setVersion("QD 2025");
        document.setContentSha256("a".repeat(64));
        assertThatThrownBy(() -> document.setIngestStatus(IngestStatus.READY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> document.setKnowledgeDocumentId("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        Document saved = documents.saveAndFlush(document);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIngestStatus()).isEqualTo(IngestStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();

        // The corpus is managed independently; this ID need not exist in knowledge_documents.
        saved.setKnowledgeDocumentId("b".repeat(64));
        saved.setIngestStatus(IngestStatus.READY);
        documents.flush();
        entityManager.clear();

        Document loaded = documents.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getUploadedBy().getId()).isEqualTo(admin.getId());
        assertThat(loaded.getOriginalFilename()).isEqualTo("quy-che.pdf");
        assertThat(loaded.getSourceUrl()).isEqualTo("https://pdt.iuh.edu.vn/quy-che-dao-tao");
        assertThat(loaded.getStorageKey()).isEqualTo("uploads/quy-che.pdf");
        assertThat(loaded.getVersion()).isEqualTo("QD 2025");
        assertThat(loaded.getContentSha256()).isEqualTo("a".repeat(64));
        assertThat(loaded.getKnowledgeDocumentId()).isEqualTo("b".repeat(64));
        assertThat(loaded.getIngestStatus()).isEqualTo(IngestStatus.READY);
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(loaded.getCreatedAt());
        assertThat(documents.findByUploadedBy_IdOrderByCreatedAtDescIdDesc(
                admin.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Document::getId).contains(saved.getId());
        assertThat(documents.findByIngestStatusOrderByCreatedAtDescIdDesc(
                IngestStatus.READY, PageRequest.of(0, 10)).getContent())
                .extracting(Document::getId).contains(saved.getId());
        assertThat(documents.findByKnowledgeDocumentId("b".repeat(64)))
                .extracting(Document::getId).contains(saved.getId());
    }
}
