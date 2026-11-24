package vn.edu.iuh.regrag.system;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.iuh.regrag.chat.AiServiceClient;

@Service
@Profile("db")
public class SystemService {

    private final JdbcTemplate jdbc;
    private final AiServiceClient aiServiceClient;

    public SystemService(JdbcTemplate jdbc, AiServiceClient aiServiceClient) {
        this.jdbc = jdbc;
        this.aiServiceClient = aiServiceClient;
    }

    public SystemHealthResponse checkHealth() {
        Map<String, SystemHealthResponse.ComponentHealth> components = new LinkedHashMap<>();

        boolean dbOk;
        try {
            Integer val = jdbc.queryForObject("SELECT 1", Integer.class);
            dbOk = (val != null && val == 1);
            components.put("database", dbOk ? SystemHealthResponse.ComponentHealth.up()
                    : SystemHealthResponse.ComponentHealth.down("Unexpected query result"));
        } catch (Exception ex) {
            dbOk = false;
            components.put("database", SystemHealthResponse.ComponentHealth.down(ex.getMessage()));
        }

        boolean aiOk;
        try {
            aiOk = aiServiceClient.isHealthy();
            components.put("aiService", aiOk ? SystemHealthResponse.ComponentHealth.up()
                    : SystemHealthResponse.ComponentHealth.down("Service endpoint unreachable or unhealthy"));
        } catch (Exception ex) {
            aiOk = false;
            components.put("aiService", SystemHealthResponse.ComponentHealth.down(ex.getMessage()));
        }

        String overallStatus;
        if (dbOk && aiOk) {
            overallStatus = "UP";
        } else if (!dbOk && !aiOk) {
            overallStatus = "DOWN";
        } else {
            overallStatus = "DEGRADED";
        }

        return new SystemHealthResponse(overallStatus, Instant.now(), components);
    }

    public SystemVersionResponse getVersion() {
        return new SystemVersionResponse(
                "iuh-regrag-backend",
                "0.0.1-SNAPSHOT",
                "running");
    }

    @Transactional(readOnly = true)
    public List<ActiveDocumentResponse> getActiveVectorDocuments() {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_documents')",
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            return List.of();
        }

        String sql = """
                SELECT d.id, d.title, d.source, d.document_group, d.so_qd, d.publication_year, d.legal_status, d.updated_at,
                       COUNT(c.id) AS chunk_count
                FROM knowledge_documents d
                LEFT JOIN knowledge_chunks c ON c.document_id = d.id
                GROUP BY d.id, d.title, d.source, d.document_group, d.so_qd, d.publication_year, d.legal_status, d.updated_at
                ORDER BY d.updated_at DESC
                """;

        return jdbc.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id") != null ? rs.getString("id").trim() : null;
            String title = rs.getString("title");
            String source = rs.getString("source");
            String documentGroup = rs.getString("document_group");
            String soQd = rs.getString("so_qd");
            Integer publicationYear = rs.getObject("publication_year", Integer.class);
            String legalStatus = rs.getString("legal_status");
            long chunkCount = rs.getLong("chunk_count");
            Timestamp ts = rs.getTimestamp("updated_at");
            Instant updatedAt = ts != null ? ts.toInstant() : null;

            return new ActiveDocumentResponse(
                    id,
                    title,
                    source,
                    documentGroup,
                    soQd,
                    publicationYear,
                    legalStatus,
                    chunkCount,
                    updatedAt);
        });
    }
}
