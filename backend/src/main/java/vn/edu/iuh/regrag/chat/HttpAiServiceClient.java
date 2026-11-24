package vn.edu.iuh.regrag.chat;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Component
@Profile("db")
public class HttpAiServiceClient implements AiServiceClient {

    private final RestClient client;

    public HttpAiServiceClient(
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.timeout-seconds}") long timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("AI service timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(httpClient);
        requests.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
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
