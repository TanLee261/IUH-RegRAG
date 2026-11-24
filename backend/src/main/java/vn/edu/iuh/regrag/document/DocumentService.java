package vn.edu.iuh.regrag.document;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.iuh.regrag.user.User;
import vn.edu.iuh.regrag.user.UserRepository;

@Service
@Profile("db")
public class DocumentService {

    private final DocumentRepository documents;
    private final UserRepository users;
    private final DocumentStorageService storage;

    public DocumentService(DocumentRepository documents, UserRepository users,
                           DocumentStorageService storage) {
        this.documents = documents;
        this.users = users;
        this.storage = storage;
    }

    @Transactional
    public DocumentResponse upload(UUID adminUserId, MultipartFile file,
                                   String title, String version, String sourceUrl) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        User admin = users.findById(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        // Create the entity first to get the ID, then store the file
        Document doc = new Document(admin, title.trim());
        if (version != null && !version.isBlank()) {
            doc.setVersion(version.trim());
        }
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            doc.setSourceUrl(sourceUrl.trim());
        }
        doc = documents.save(doc);

        // Store the file and update metadata
        DocumentStorageService.StoredFile stored = storage.store(doc.getId(), file);
        doc.setStorageKey(stored.storageKey());
        doc.setOriginalFilename(stored.originalFilename());
        doc.setContentSha256(stored.contentSha256());
        doc = documents.save(doc);

        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(IngestStatus status, Pageable pageable) {
        Page<Document> page;
        if (status != null) {
            page = documents.findByIngestStatusOrderByCreatedAtDescIdDesc(status, pageable);
        } else {
            page = documents.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return page.map(DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id) {
        Document doc = findOrThrow(id);
        return DocumentResponse.from(doc);
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = findOrThrow(id);
        storage.delete(doc.getStorageKey());
        documents.delete(doc);
    }

    @Transactional
    public DocumentResponse updateIngestStatus(UUID id, UpdateIngestStatusRequest request) {
        Document doc = findOrThrow(id);

        if (request.status() == IngestStatus.READY && request.knowledgeDocumentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "knowledgeDocumentId is required when status is READY");
        }

        if (request.knowledgeDocumentId() != null) {
            doc.setKnowledgeDocumentId(request.knowledgeDocumentId());
        }
        if (request.ingestError() != null) {
            doc.setIngestError(request.ingestError().isBlank() ? null : request.ingestError());
        }
        doc.setIngestStatus(request.status());
        doc = documents.save(doc);
        return DocumentResponse.from(doc);
    }

    private Document findOrThrow(UUID id) {
        return documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + id));
    }
}
