package com.chatroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls LLM provider's embedding API to generate vector embeddings.
 * Uses OpenAI-compatible /v1/embeddings endpoint.
 * <p>
 * Now uses the shared pooled {@link HttpClient} (injected via constructor)
 * instead of a non-pooled {@code RestTemplate}.  This eliminates the per-request
 * TCP+TLS handshake overhead that previously added 50-200ms to every RAG
 * embedding call.
 */
@Slf4j
@Service
public class EmbeddingService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public EmbeddingService(HttpClient pooledHttpClient) {
        this.httpClient = pooledHttpClient;
    }

    /**
     * Generate embedding vector for text.
     * Returns null if embedding API is not available or fails.
     */
    public List<Double> embed(String apiEndpoint, String apiKey,
                               String model, String text) {
        // Derive embedding endpoint from chat endpoint
        String embeddingEndpoint = apiEndpoint
                .replace("/chat/completions", "/embeddings")
                .replace("/compatible-mode/v1/chat/completions", "/compatible-mode/v1/embeddings");
        String embeddingModel = inferEmbeddingModel(model);

        try {
            Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text.length() > 8000 ? text.substring(0, 8000) : text
            );
            String json = objectMapper.writeValueAsString(body);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingEndpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respMap = objectMapper.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) respMap.get("data");
                if (data != null && !data.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                    if (embedding != null && !embedding.isEmpty()) {
                        log.debug("Generated embedding: {} dimensions", embedding.size());
                        return embedding;
                    }
                }
            } else {
                log.debug("Embedding API returned {} for {}", response.statusCode(), embeddingEndpoint);
            }
        } catch (Exception e) {
            log.debug("Embedding API not available for {}: {}", embeddingEndpoint, e.getMessage());
        }
        return null;
    }

    private String inferEmbeddingModel(String chatModel) {
        if (chatModel == null) return "text-embedding-3-small";
        String m = chatModel.toLowerCase();
        if (m.contains("gpt") || m.contains("openai")) return "text-embedding-3-small";
        if (m.contains("qwen")) return "text-embedding-v2";
        if (m.contains("glm")) return "embedding-2";
        if (m.contains("deepseek")) return "text-embedding-3-small"; // DeepSeek doesn't have embeddings
        return "text-embedding-3-small";
    }
}
