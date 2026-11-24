package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChatRequestValidationUnitTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void allowsValidQuestionAndSessionId() {
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                "Sinh viên năm nhất được đăng ký tối đa bao nhiêu tín chỉ?",
                UUID.randomUUID());

        Set<ConstraintViolation<ChatController.ChatRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void allowsValidQuestionWithNullSessionIdForNewConversations() {
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                "Điều kiện tốt nghiệp theo quy chế mới?",
                null);

        Set<ConstraintViolation<ChatController.ChatRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsNullQuestion() {
        ChatController.ChatRequest request = new ChatController.ChatRequest(null, UUID.randomUUID());

        Set<ConstraintViolation<ChatController.ChatRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        ConstraintViolation<ChatController.ChatRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("question");
    }

    @Test
    void rejectsEmptyOrWhitespaceOnlyQuestion() {
        ChatController.ChatRequest emptyRequest = new ChatController.ChatRequest("", UUID.randomUUID());
        Set<ConstraintViolation<ChatController.ChatRequest>> emptyViolations = validator.validate(emptyRequest);
        assertThat(emptyViolations).isNotEmpty();
        assertThat(emptyViolations.iterator().next().getPropertyPath().toString()).isEqualTo("question");

        ChatController.ChatRequest whitespaceRequest = new ChatController.ChatRequest(
                "   \n\t  \r  ", UUID.randomUUID());
        Set<ConstraintViolation<ChatController.ChatRequest>> wsViolations = validator.validate(whitespaceRequest);
        assertThat(wsViolations).isNotEmpty();
        assertThat(wsViolations.iterator().next().getPropertyPath().toString()).isEqualTo("question");
    }

    @Test
    void rejectsQuestionExceeding2000Characters() {
        String exact2000 = "a".repeat(2000);
        ChatController.ChatRequest valid2000 = new ChatController.ChatRequest(exact2000, null);
        assertThat(validator.validate(valid2000)).isEmpty();

        String oversized2001 = "a".repeat(2001);
        ChatController.ChatRequest invalid2001 = new ChatController.ChatRequest(oversized2001, null);
        Set<ConstraintViolation<ChatController.ChatRequest>> violations = validator.validate(invalid2001);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("question");
    }
}
