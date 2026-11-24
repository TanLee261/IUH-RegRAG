package vn.edu.iuh.regrag.feedback;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public FeedbackResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FeedbackRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return feedbackService.submitFeedback(userId, request);
    }

    @GetMapping("/messages/{messageId}")
    public FeedbackResponse getByMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return feedbackService.getFeedbackByMessage(userId, messageId);
    }
}
