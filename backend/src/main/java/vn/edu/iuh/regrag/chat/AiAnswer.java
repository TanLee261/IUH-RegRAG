package vn.edu.iuh.regrag.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiAnswer(String answer, String status, List<Source> sources) {

    public AiAnswer {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("AI answer must not be blank");
        }
        status = status == null || status.isBlank() ? "answered" : status;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Source(
            @JsonProperty("chunk_id") String chunkId,
            @JsonProperty("document_id") String documentId,
            String title,
            String dieu,
            String khoan,
            String source,
            String snippet,
            Double score) {
    }
}
