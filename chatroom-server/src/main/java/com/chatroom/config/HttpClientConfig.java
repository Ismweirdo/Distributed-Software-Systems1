package com.chatroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared JDK HttpClient with connection pooling for all outbound HTTP calls.
 * <p>
 * One HttpClient instance = one connection pool (JDK manages this internally).
 * All services (LLMApiClient, EmbeddingService, SkillFolderService, etc.)
 * share this single pooled instance, eliminating per-request TCP+TLS handshake
 * overhead.
 * <p>
 * Pooling behavior (JDK 17+):
 * - HTTP/1.1: connection-per-route pool, size limited by available connections
 * - HTTP/2:   multiplexed single connection per host
 * - Idle connections are evicted after keep-alive timeout (default ~1200s)
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient pooledHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)  // HTTP/1.1 for broad LLM API compatibility
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }
}
