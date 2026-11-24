package vn.edu.iuh.regrag.feedback;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.iuh.regrag.chat.ChatSession;
import vn.edu.iuh.regrag.chat.Message;
import vn.edu.iuh.regrag.chat.MessageRepository;
import vn.edu.iuh.regrag.chat.MessageRole;

@Service
@Profile("db")
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final MessageRepository messageRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, MessageRepository messageRepository) {
        this.feedbackRepository = feedbackRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public FeedbackResponse submitFeedback(UUID userId, FeedbackRequest request) {
        Message message = findAndValidateMessage(userId, request.messageId());

        Optional<Feedback> existing = feedbackRepository.findByMessage_Id(request.messageId());
        Feedback target;
        if (existing.isPresent()) {
            target = existing.get();
            target.setRating(request.rating());
            target.setComment(request.comment());
        } else {
            target = new Feedback(message, request.rating(), request.comment());
        }

        Feedback saved = feedbackRepository.save(target);
        return FeedbackResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getFeedbackByMessage(UUID userId, UUID messageId) {
        findAndValidateMessage(userId, messageId);

        Feedback feedback = feedbackRepository.findByMessage_Id(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found for message"));
        return FeedbackResponse.from(feedback);
    }

    private Message findAndValidateMessage(UUID userId, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback is only allowed for assistant messages");
        }

        ChatSession session = message.getSession();
        if (session == null || session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }

        return message;
    }
}
