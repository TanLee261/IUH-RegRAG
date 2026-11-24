package vn.edu.iuh.regrag.system;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * Public healthcheck endpoint for CI/CD, docker-compose, and monitoring.
     * Returns 200 OK when UP or DEGRADED, or 503 SERVICE_UNAVAILABLE when completely DOWN.
     */
    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> health() {
        SystemHealthResponse health = systemService.checkHealth();
        if ("DOWN".equals(health.status())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
        return ResponseEntity.ok(health);
    }

    /**
     * Public system version & metadata.
     */
    @GetMapping("/version")
    public SystemVersionResponse version() {
        return systemService.getVersion();
    }

    /**
     * Admin endpoint: list regulatory documents currently active in the vector database (knowledge_documents).
     */
    @GetMapping("/documents")
    public List<ActiveDocumentResponse> listVectorDocuments() {
        return systemService.getActiveVectorDocuments();
    }
}
