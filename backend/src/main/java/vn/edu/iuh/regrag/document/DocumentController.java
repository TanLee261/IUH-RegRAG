package vn.edu.iuh.regrag.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("db")
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload a new document (PDF, TXT, DOCX, ...).
     * Required role: ADMIN.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") @Size(max = 500) String title,
            @RequestParam(value = "version", required = false) @Size(max = 100) String version,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        return documentService.upload(adminId, file, title, version, sourceUrl);
    }

    /**
     * List documents, optionally filtered by ingestStatus, with pagination.
     * Required role: ADMIN.
     */
    @GetMapping
    public Page<DocumentResponse> list(
            @RequestParam(required = false) IngestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.list(status, PageRequest.of(page, Math.min(size, 100)));
    }

    /**
     * Get detail of a single document by ID.
     * Required role: ADMIN.
     */
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    /**
     * Delete a document and its physical file.
     * Required role: ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.delete(id);
    }

    /**
     * Update the ingest status of a document (called by pipeline integration or admin UI).
     * Required role: ADMIN.
     */
    @PatchMapping("/{id}/status")
    public DocumentResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIngestStatusRequest request) {
        return documentService.updateIngestStatus(id, request);
    }
}
