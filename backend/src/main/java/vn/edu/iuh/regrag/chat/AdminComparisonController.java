package vn.edu.iuh.regrag.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/admin/comparisons")
public class AdminComparisonController {

    private final ChatConversationStore conversations;

    public AdminComparisonController(ChatConversationStore conversations) {
        this.conversations = conversations;
    }

    @GetMapping("/stats")
    public ComparisonStatsResponse getStats() {
        return conversations.getComparisonStats();
    }

    public record ModelScoreSummary(
            String model,
            long wins,
            long losses,
            long ties,
            long bothBad,
            long totalVoted,
            double winRate) {
    }

    public record RecentComparisonItem(
            UUID id,
            String question,
            String modelA,
            String modelB,
            CompareWinner winner,
            Instant votedAt,
            Instant createdAt) {
    }

    public record ComparisonStatsResponse(
            long totalComparisons,
            long totalVoted,
            List<ModelScoreSummary> models,
            List<RecentComparisonItem> recent) {
    }
}
