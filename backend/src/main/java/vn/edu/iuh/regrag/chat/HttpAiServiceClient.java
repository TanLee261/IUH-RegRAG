package vn.edu.iuh.regrag.chat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("db")
public class HttpAiServiceClient implements AiServiceClient {

    private final RestClient client;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public HttpAiServiceClient(
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.timeout-seconds}") long timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("AI service timeout must be positive");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(this.httpClient);
        requests.setReadTimeout(this.timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requests)
                .build();
    }

    @Override
    public AiAnswer answer(String question) {
        try {
            AiAnswer response = client.post()
                    .uri("/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", question))
                    .retrieve()
                    .body(AiAnswer.class);
            if (response == null) {
                throw new RestClientException("AI service returned an empty response");
            }
            return response;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AI service unavailable", exception);
        }
    }

    @Override
    public void streamAnswer(String question, Consumer<AiStreamEvent> onEvent) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("question", question));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/answer/stream"))
                    .timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI service unavailable (HTTP " + response.statusCode() + ")");
            }

            try (Stream<String> lines = response.body()) {
                lines.forEach(rawLine -> {
                    String line = rawLine.trim();
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty()) {
                        return;
                    }
                    JsonNode node;
                    try {
                        node = objectMapper.readTree(payload);
                    } catch (IOException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, "Invalid AI stream payload", ex);
                    }
                    String type = node.path("type").asText();
                    switch (type) {
                        case "token" -> {
                            String content = node.path("content").asText("");
                            if (!content.isEmpty()) {
                                onEvent.accept(AiStreamEvent.token(content));
                            }
                        }
                        case "done" -> {
                            try {
                                AiAnswer answer = objectMapper.treeToValue(node, AiAnswer.class);
                                onEvent.accept(AiStreamEvent.done(answer));
                            } catch (IOException | IllegalArgumentException ex) {
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY, "Invalid AI done payload", ex);
                            }
                        }
                        case "error" -> throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                node.path("message").asText("AI service unavailable"));
                        default -> {
                            // Ignore unknown SSE event types
                        }
                    }
                });
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AI service stream interrupted", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AI service unavailable", ex);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            var response = client.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            return false;
        }
    }
}
