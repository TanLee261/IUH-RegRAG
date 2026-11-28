package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HttpAiServiceClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidTimeoutInConstructor() {
        assertThatThrownBy(() -> new HttpAiServiceClient("http://localhost:" + port, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> new HttpAiServiceClient("http://localhost:" + port, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void successfullyDeserializesAnswerAndCitations() {
        String jsonResponse = """
                {
                  "answer": "Sinh viên tích lũy đủ 110 tín chỉ được đăng ký làm khóa luận.",
                  "status": "answered",
                  "sources": [
                    {
                      "chunk_id": "chunk-22-1",
                      "document_id": "doc-dao-tao-2024",
                      "title": "Quy chế đào tạo",
                      "dieu": "22",
                      "khoan": "1",
                      "source": "https://iuh.edu.vn/quy-che.pdf",
                      "snippet": "Đăng ký làm khóa luận tốt nghiệp.",
                      "score": 0.93
                    }
                  ]
                }
                """;

        server.createContext("/answer", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);
        AiAnswer answer = client.answer("Điều kiện làm khóa luận?");

        assertThat(answer.answer()).contains("110 tín chỉ");
        assertThat(answer.status()).isEqualTo("answered");
        assertThat(answer.sources()).hasSize(1);
        AiAnswer.Source source = answer.sources().get(0);
        assertThat(source.chunkId()).isEqualTo("chunk-22-1");
        assertThat(source.dieu()).isEqualTo("22");
        assertThat(source.khoan()).isEqualTo("1");
        assertThat(source.score()).isEqualTo(0.93);
    }

    @Test
    void throwsBadGatewayWhenAiServiceReturns500Error() {
        server.createContext("/answer", exchange -> {
            byte[] bytes = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);

        assertThatThrownBy(() -> client.answer("Bất kỳ câu hỏi nào"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(rse.getReason()).contains("AI service unavailable");
                });
    }

    @Test
    void throwsBadGatewayWhenAiServiceReturnsEmptyResponse() {
        server.createContext("/answer", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, -1); // No body
            exchange.close();
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);

        assertThatThrownBy(() -> client.answer("Câu hỏi rỗng"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void throwsBadGatewayWhenAiServiceUnreachable() {
        int nonExistentPort = port + 1000;
        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + nonExistentPort, 2);

        assertThatThrownBy(() -> client.answer("Kết nối thất bại"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void isHealthyReturnsTrueOn200AndFalseOnError() {
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);
        assertThat(client.isHealthy()).isTrue();

        // When health check fails with 503
        server.removeContext("/health");
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        assertThat(client.isHealthy()).isFalse();

        // When server is stopped completely
        server.stop(0);
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void streamsTokensAndDoneEventFromSseEndpoint() {
        String sseBody = String.join("\n",
                "data: {\"type\":\"token\",\"content\":\"Điều kiện \"}",
                "",
                "data: {\"type\":\"token\",\"content\":\"xét tốt nghiệp.\"}",
                "",
                "data: {\"type\":\"done\",\"answer\":\"Điều kiện xét tốt nghiệp.\",\"status\":\"answered\",\"provider\":\"gemini\",\"sources\":[{\"chunk_id\":\"chunk-22-1\",\"document_id\":\"doc-1\",\"title\":\"Quy chế đào tạo\",\"dieu\":\"22\",\"khoan\":\"1\",\"source\":\"https://iuh.edu.vn/qc.pdf\",\"snippet\":\"Tích lũy đủ tín chỉ.\",\"score\":0.95}]}",
                "",
                "");

        server.createContext("/answer/stream", exchange -> {
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);
        List<AiServiceClient.AiStreamEvent> events = new ArrayList<>();
        client.streamAnswer("Điều kiện xét tốt nghiệp?", events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).type()).isEqualTo("token");
        assertThat(events.get(0).content()).isEqualTo("Điều kiện ");
        assertThat(events.get(1).content()).isEqualTo("xét tốt nghiệp.");
        assertThat(events.get(2).type()).isEqualTo("done");
        assertThat(events.get(2).answer().answer()).isEqualTo("Điều kiện xét tốt nghiệp.");
        assertThat(events.get(2).answer().sources()).hasSize(1);
        assertThat(events.get(2).answer().sources().get(0).dieu()).isEqualTo("22");
    }

    @Test
    void streamsDualModelCompareEventsFromSseEndpoint() {
        String sseBody = String.join("\n",
                "data: {\"type\":\"token\",\"slot\":\"A\",\"content\":\"Qwen \"}",
                "data: {\"type\":\"token\",\"slot\":\"B\",\"content\":\"Gemma \"}",
                "data: {\"type\":\"done\",\"slot\":\"A\",\"model\":\"qwen2.5:3b\",\"provider\":\"ollama\",\"answer\":\"Qwen trả lời.\",\"status\":\"answered\",\"sources\":[]}",
                "data: {\"type\":\"done\",\"slot\":\"B\",\"model\":\"gemma3:4b\",\"provider\":\"ollama\",\"answer\":\"Gemma trả lời.\",\"status\":\"answered\",\"sources\":[]}",
                "");

        server.createContext("/answer/compare/stream", exchange -> {
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        HttpAiServiceClient client = new HttpAiServiceClient("http://localhost:" + port, 5);
        List<AiServiceClient.CompareStreamEvent> events = new ArrayList<>();
        client.compareStreamAnswer("Điều kiện tốt nghiệp?", events::add);

        assertThat(events).hasSize(4);
        assertThat(events.get(0).slot()).isEqualTo("A");
        assertThat(events.get(1).slot()).isEqualTo("B");
        assertThat(events.get(2).model()).isEqualTo("qwen2.5:3b");
        assertThat(events.get(3).model()).isEqualTo("gemma3:4b");
    }
}
