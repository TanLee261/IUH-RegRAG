package vn.edu.iuh.regrag.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic Mock AI Service Client for testing without external LLM/Python service.
 * Activated via Spring profile "mock-ai".
 */
@Component
@Profile("mock-ai")
@Primary
public class MockAiServiceClient implements AiServiceClient {

    private boolean healthy = true;
    private boolean simulateFailure = false;
    private final List<String> recordedQuestions = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AiAnswer> customResponses = new ConcurrentHashMap<>();

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void registerResponse(String questionSubstr, AiAnswer answer) {
        customResponses.put(questionSubstr.toLowerCase(), answer);
    }

    public void reset() {
        this.healthy = true;
        this.simulateFailure = false;
        this.recordedQuestions.clear();
        this.customResponses.clear();
    }

    public List<String> getRecordedQuestions() {
        return List.copyOf(recordedQuestions);
    }

    @Override
    public boolean isHealthy() {
        return healthy && !simulateFailure;
    }

    @Override
    public AiAnswer answer(String question) {
        recordedQuestions.add(question);

        if (simulateFailure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mock AI service simulated error");
        }

        String lower = question.toLowerCase();

        // Check custom configured responses first
        for (Map.Entry<String, AiAnswer> entry : customResponses.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Built-in intelligent fixtures based on IUH regulations
        if (lower.contains("tốt nghiệp") || lower.contains("khóa luận")) {
            return new AiAnswer(
                    "Theo Điều 22 Quy chế đào tạo của Trường Đại học Công nghiệp TP.HCM, sinh viên tích lũy đủ 110 tín chỉ và có điểm trung bình tích lũy từ 2.50 trở lên được đăng ký thực hiện khóa luận tốt nghiệp.",
                    "answered",
                    List.of(new AiAnswer.Source(
                            "chunk-dieu-22-1", "doc-dao-tao-2024", "Quy chế đào tạo đại học chính quy 2024",
                            "22", "1", "https://iuh.edu.vn/quy-che-2024.pdf",
                            "Sinh viên tích lũy đủ 110 tín chỉ và có điểm trung bình tích lũy từ 2.50 trở lên được đăng ký thực hiện khóa luận tốt nghiệp.",
                            0.94
                    ))
            );
        }

        if (lower.contains("cảnh báo") || lower.contains("buộc thôi học")) {
            return new AiAnswer(
                    "Theo Điều 18 Quy chế đào tạo, sinh viên bị cảnh báo học tập nếu điểm trung bình học kỳ dưới 1.00 (học kỳ đầu) hoặc dưới 1.20 (các học kỳ tiếp theo). Sinh viên bị cảnh báo học tập 2 lần liên tiếp sẽ bị buộc thôi học.",
                    "answered",
                    List.of(new AiAnswer.Source(
                            "chunk-dieu-18-2", "doc-dao-tao-2024", "Quy chế đào tạo đại học chính quy 2024",
                            "18", "2", "https://iuh.edu.vn/quy-che-2024.pdf",
                            "Cảnh báo kết quả học tập và xử lý học vụ.",
                            0.92
                    ))
            );
        }

        if (lower.contains("tín chỉ") || lower.contains("học phần") || lower.contains("rút")) {
            return new AiAnswer(
                    "Theo Điều 14 Quy chế đào tạo, sinh viên xếp hạng học lực bình thường được đăng ký tối thiểu 14 tín chỉ và tối đa 25 tín chỉ cho mỗi học kỳ chính. Sinh viên được phép rút học phần trước tuần thứ 4 của học kỳ.",
                    "answered",
                    List.of(new AiAnswer.Source(
                            "chunk-dieu-14-1", "doc-dao-tao-2024", "Quy chế đào tạo đại học chính quy 2024",
                            "14", "1", "https://iuh.edu.vn/quy-che-2024.pdf",
                            "Đăng ký học phần và khối lượng học tập tối thiểu, tối đa.",
                            0.91
                    ))
            );
        }

        if (lower.contains("thời tiết") || lower.contains("chứng khoán") || lower.contains("nấu ăn")) {
            return new AiAnswer(
                    "Xin lỗi, nội dung câu hỏi không nằm trong phạm vi các quy chế đào tạo và học vụ của Trường Đại học Công nghiệp TP.HCM.",
                    "insufficient_context",
                    List.of()
            );
        }

        // Generic fallback fixture
        return new AiAnswer(
                "Nội dung quy định được trích dẫn từ văn bản quy chế chính thức của Trường Đại học Công nghiệp TP.HCM.",
                "answered",
                List.of(new AiAnswer.Source(
                        "chunk-generic-1", "doc-generic-2024", "Văn bản quy chế IUH",
                        "1", "1", "https://iuh.edu.vn/quy-che-chung.pdf",
                        "Nội dung điều khoản quy định chung.",
                        0.85
                ))
        );
    }
}
