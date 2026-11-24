package vn.edu.iuh.regrag.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound to LLM_API_KEY or GEMINI_API_KEY for provider calls. */
@ConfigurationProperties(prefix = "app.llm")
public record LlmCredentials(String apiKey) {

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is required to call an LLM provider");
        }
        return apiKey;
    }
}
