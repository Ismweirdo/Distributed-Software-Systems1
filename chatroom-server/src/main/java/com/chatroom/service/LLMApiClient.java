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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Async LLM API client using JDK HttpClient (non-blocking I/O).
 * <p>
 * Key improvement over the old RestTemplate-based implementation:
 * - Uses JDK HttpClient's async {@code sendAsync()} — no thread blocked on socket I/O.
 * - Single shared connection pool (via injected {@link HttpClient} bean) eliminates
 *   TCP+TLS handshake overhead for each request.
 * - {@code chatAsync()} returns {@code CompletableFuture<String>} so callers can
 *   chain without occupying a thread during the 2-5s LLM response window.
 * - SSE streaming via {@code HttpResponse.BodyHandlers.ofLines()} — tokens are
 *   delivered to the callback as they arrive from the network.
 * <p>
 * Sync wrappers ({@code chat()}, {@code chatStream()}) are kept for backward
 * compatibility but should be migrated to the async variants over time.
 */
@Slf4j
@Service
public class LLMApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** LLM response timeout — generous to handle long-form generation. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);

    public LLMApiClient(HttpClient pooledHttpClient) {
        this.httpClient = pooledHttpClient;
    }

    // ==================== Async API (preferred) ====================

    /**
     * Send a chat completion request asynchronously.
     * <p>
     * The returned CompletableFuture completes on the HttpClient's I/O thread
     * when the full LLM response body is available.  No thread from the
     * application pool is occupied while waiting for the LLM.
     *
     * @return CompletableFuture with the LLM reply text
     */
    public CompletableFuture<String> chatAsync(String apiEndpoint, String apiKey, String model,
                                                List<Map<String, String>> messages,
                                                int maxTokens, double temperature) {
        Map<String, Object> body = buildRequestBody(model, messages, maxTokens, temperature, false);
        HttpRequest request = buildPost(apiEndpoint, apiKey, body);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::extractContent)
                .exceptionally(e -> {
                    log.error("LLM async call failed: {}", e.getMessage());
                    throw new RuntimeException("LLM API error: " + e.getMessage(), e);
                });
    }

    /**
     * Send a streaming chat completion asynchronously.
     * <p>
     * {@code onChunk} is called from the HttpClient's thread as each SSE token
     * arrives from the network — enabling true real-time streaming without
     * blocking any application thread.
     *
     * @param onChunk callback for each token as it arrives
     * @return CompletableFuture that completes with the full assembled text
     */
    public CompletableFuture<String> chatStreamAsync(String apiEndpoint, String apiKey, String model,
                                                      List<Map<String, String>> messages,
                                                      int maxTokens, double temperature,
                                                      Consumer<String> onChunk) {
        Map<String, Object> body = buildRequestBody(model, messages, maxTokens, temperature, true);
        HttpRequest request = buildPost(apiEndpoint, apiKey, body);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(response -> {
                    StringBuilder fullText = new StringBuilder();
                    try {
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    return; // continue forEach
                                }
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> choices =
                                            (List<Map<String, Object>>) chunk.get("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> delta =
                                                (Map<String, Object>) choices.get(0).get("delta");
                                        if (delta != null && delta.containsKey("content")) {
                                            String token = (String) delta.get("content");
                                            fullText.append(token);
                                            onChunk.accept(token);
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // Skip unparseable SSE chunks
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.error("LLM stream processing failed: {}", e.getMessage());
                        throw new RuntimeException("LLM stream error: " + e.getMessage(), e);
                    }
                    return fullText.toString();
                });
    }

    // ==================== Sync API (backward compatibility) ====================

    /**
     * Synchronous chat completion — convenience wrapper around {@link #chatAsync}.
     * <p>
     * Prefer {@link #chatAsync} when the calling thread should not be blocked.
     */
    public String chat(String apiEndpoint, String apiKey, String model,
                       List<Map<String, String>> messages,
                       int maxTokens, double temperature) {
        try {
            return chatAsync(apiEndpoint, apiKey, model, messages, maxTokens, temperature).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM API error: " + cause.getMessage(), cause);
        }
    }

    /**
     * Synchronous streaming chat completion — convenience wrapper around {@link #chatStreamAsync}.
     * <p>
     * Prefer {@link #chatStreamAsync} when the calling thread should not be blocked.
     */
    public String chatStream(String apiEndpoint, String apiKey, String model,
                             List<Map<String, String>> messages,
                             int maxTokens, double temperature,
                             Consumer<String> onChunk) {
        try {
            return chatStreamAsync(apiEndpoint, apiKey, model, messages, maxTokens, temperature, onChunk).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM stream error: " + cause.getMessage(), cause);
        }
    }

    // ==================== helpers ====================

    private Map<String, Object> buildRequestBody(String model, List<Map<String, String>> messages,
                                                  int maxTokens, double temperature, boolean stream) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private HttpRequest buildPost(String apiEndpoint, String apiKey, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            return builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build LLM request", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            String detail = response.body();
            if (detail != null && detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            throw new RuntimeException("LLM API returned HTTP " + response.statusCode()
                    + ": " + (detail != null ? detail : "(no body)"));
        }
        try {
            Map<String, Object> map = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse LLM response: " + e.getMessage(), e);
        }
        return null;
    }
}
