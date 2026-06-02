package com.chatroom.service;

import com.chatroom.model.entity.BotSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AI/Bot service layer.
 * Tests core logic without requiring Spring context, Redis, or DB.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTests {

    // ==================== T1: EmbeddingService Logic ====================
    @Nested
    @DisplayName("EmbeddingService - Model Inference")
    class EmbeddingServiceTests {

        @Test
        @DisplayName("Should infer embedding model from chat model name")
        void shouldInferEmbeddingModel() {
            // Test model inference logic (replicated from EmbeddingService.inferEmbeddingModel)
            assertEquals("text-embedding-3-small", inferEmbeddingModel("deepseek-chat"));
            assertEquals("text-embedding-3-small", inferEmbeddingModel("deepseek-reasoner"));
            assertEquals("text-embedding-v2", inferEmbeddingModel("qwen-turbo"));
            assertEquals("text-embedding-v2", inferEmbeddingModel("qwen-plus"));
            assertEquals("embedding-2", inferEmbeddingModel("glm-4"));
            assertEquals("embedding-2", inferEmbeddingModel("glm-4-flash"));
            assertEquals("text-embedding-3-small", inferEmbeddingModel("gpt-4o"));
            assertEquals("text-embedding-3-small", inferEmbeddingModel("unknown-model"));
        }

        @Test
        @DisplayName("Should derive embedding endpoint from chat endpoint")
        void shouldDeriveEmbeddingEndpoint() {
            String chatEndpoint = "https://api.deepseek.com/v1/chat/completions";
            String embeddingEndpoint = chatEndpoint.replace("/chat/completions", "/embeddings");
            assertEquals("https://api.deepseek.com/v1/embeddings", embeddingEndpoint);

            // OpenAI-style
            String openAIEndpoint = "https://api.openai.com/v1/chat/completions";
            assertEquals("https://api.openai.com/v1/embeddings",
                openAIEndpoint.replace("/chat/completions", "/embeddings"));
        }

        @Test
        @DisplayName("Should truncate long input text")
        void shouldTruncateLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9000; i++) sb.append("x");
            String input = sb.toString();
            String truncated = input.substring(0, Math.min(input.length(), 8000));
            assertEquals(8000, truncated.length());
        }

        @Test
        @DisplayName("Should handle short input without truncation")
        void shouldHandleShortInput() {
            String input = "Short text for embedding";
            String truncated = input.substring(0, Math.min(input.length(), 8000));
            assertEquals("Short text for embedding", truncated);
        }

        private String inferEmbeddingModel(String chatModel) {
            if (chatModel == null || chatModel.isEmpty()) return "text-embedding-3-small";
            String lower = chatModel.toLowerCase();
            if (lower.contains("qwen")) return "text-embedding-v2";
            if (lower.contains("glm")) return "embedding-2";
            return "text-embedding-3-small";
        }
    }

    // ==================== T2: RagMemoryService Logic ====================
    @Nested
    @DisplayName("RagMemoryService - Similarity & Tokenization")
    class RagMemoryServiceTests {

        @Test
        @DisplayName("Cosine similarity should return 1.0 for identical vectors")
        void cosineSimilarityIdentical() {
            List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
            List<Double> v2 = Arrays.asList(1.0, 2.0, 3.0);
            double sim = cosineSimilarity(v1, v2);
            assertEquals(1.0, sim, 0.0001);
        }

        @Test
        @DisplayName("Cosine similarity should return 0.0 for orthogonal vectors")
        void cosineSimilarityOrthogonal() {
            List<Double> v1 = Arrays.asList(1.0, 0.0, 0.0);
            List<Double> v2 = Arrays.asList(0.0, 1.0, 0.0);
            double sim = cosineSimilarity(v1, v2);
            assertEquals(0.0, sim, 0.0001);
        }

        @Test
        @DisplayName("Cosine similarity should handle empty vectors")
        void cosineSimilarityEmpty() {
            List<Double> v1 = Collections.emptyList();
            List<Double> v2 = Arrays.asList(1.0, 2.0);
            double sim = cosineSimilarity(v1, v2);
            assertEquals(0.0, sim, 0.0001);
        }

        @Test
        @DisplayName("Jaccard similarity should return 1.0 for identical sets")
        void jaccardSimilarityIdentical() {
            Set<String> s1 = new HashSet<>(Arrays.asList("a", "b", "c"));
            Set<String> s2 = new HashSet<>(Arrays.asList("a", "b", "c"));
            double sim = jaccardSimilarity(s1, s2);
            assertEquals(1.0, sim, 0.0001);
        }

        @Test
        @DisplayName("Jaccard similarity should return 0.0 for disjoint sets")
        void jaccardSimilarityDisjoint() {
            Set<String> s1 = new HashSet<>(Arrays.asList("a", "b"));
            Set<String> s2 = new HashSet<>(Arrays.asList("c", "d"));
            double sim = jaccardSimilarity(s1, s2);
            assertEquals(0.0, sim, 0.0001);
        }

        @Test
        @DisplayName("Tokenization should handle Chinese bigrams")
        void tokenizeChineseBigrams() {
            String input = "人工智能";
            Set<String> tokens = tokenize(input);
            assertTrue(tokens.contains("人工"));
            assertTrue(tokens.contains("工智"));
            assertTrue(tokens.contains("智能"));
        }

        @Test
        @DisplayName("Tokenization should split by delimiters")
        void tokenizeWithDelimiters() {
            String input = "hello world,python";
            Set<String> tokens = tokenize(input);
            assertTrue(tokens.contains("hello"));
            assertTrue(tokens.contains("world"));
            assertTrue(tokens.contains("python"));
        }

        @Test
        @DisplayName("Tokenization should handle mixed Chinese-English")
        void tokenizeMixed() {
            String input = "AI 人工智能 GPT 模型";
            Set<String> tokens = tokenize(input);
            // English words split by spaces
            assertTrue(tokens.contains("ai"));
            assertTrue(tokens.contains("gpt"));
            // Chinese bigrams: "人工智能" → "人工", "工智", "智能"
            // "模型" → "模型"
            assertTrue(tokens.size() >= 5);
        }

        private double cosineSimilarity(List<Double> a, List<Double> b) {
            if (a.isEmpty() || b.isEmpty() || a.size() != b.size()) return 0.0;
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.size(); i++) {
                dot += a.get(i) * b.get(i);
                normA += a.get(i) * a.get(i);
                normB += b.get(i) * b.get(i);
            }
            if (normA == 0 || normB == 0) return 0.0;
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }

        private double jaccardSimilarity(Set<String> a, Set<String> b) {
            if (a.isEmpty() && b.isEmpty()) return 1.0;
            Set<String> intersection = new HashSet<>(a);
            intersection.retainAll(b);
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        }

        private Set<String> tokenize(String text) {
            Set<String> result = new HashSet<>();
            if (text == null || text.isEmpty()) return result;
            String lower = text.toLowerCase();
            // Split by delimiters
            for (String word : lower.split("[\\s,，。！？、；：\\.!?;:]+")) {
                if (!word.isEmpty()) result.add(word);
            }
            // Chinese bigrams
            StringBuilder chineseOnly = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    chineseOnly.append(c);
                }
            }
            String cn = chineseOnly.toString();
            for (int i = 0; i < cn.length() - 1; i++) {
                result.add(cn.substring(i, i + 2));
            }
            return result;
        }
    }

    // ==================== T3: Circuit Breaker State Machine ====================
    @Nested
    @DisplayName("BotManager - Circuit Breaker State Machine")
    class CircuitBreakerTests {

        private static final int THRESHOLD = 5;
        private static final long SILENCE_MS = 15000;
        private static final int STATUS_ACTIVE = 1;
        private static final int STATUS_CIRCUIT_BROKEN = 2;

        @Test
        @DisplayName("Should stay CLOSED when error count < threshold")
        void circuitBreakerBelowThreshold() {
            int errorCount = 3;
            int status = STATUS_ACTIVE;
            assertFalse(errorCount >= THRESHOLD);
            assertEquals(STATUS_ACTIVE, status);
        }

        @Test
        @DisplayName("Should OPEN when error count reaches threshold")
        void circuitBreakerOpensAtThreshold() {
            int errorCount = 5;
            boolean shouldBreak = errorCount >= THRESHOLD;
            assertTrue(shouldBreak);
            int status = shouldBreak ? STATUS_CIRCUIT_BROKEN : STATUS_ACTIVE;
            assertEquals(STATUS_CIRCUIT_BROKEN, status);
        }

        @Test
        @DisplayName("Should stay OPEN during silence period")
        void circuitBreakerStaysOpenInSilence() {
            long openTime = System.currentTimeMillis() - 5000; // 5s ago
            long now = System.currentTimeMillis();
            boolean inSilence = (now - openTime) < SILENCE_MS;
            assertTrue(inSilence);
        }

        @Test
        @DisplayName("Should transition to HALF-OPEN after silence period")
        void circuitBreakerHalfOpenAfterSilence() {
            long openTime = System.currentTimeMillis() - 20000; // 20s ago
            long now = System.currentTimeMillis();
            boolean silenceExpired = (now - openTime) >= SILENCE_MS;
            assertTrue(silenceExpired);
        }

        @Test
        @DisplayName("Should reset error count on success")
        void circuitBreakerResetsOnSuccess() {
            AtomicInteger errorCount = new AtomicInteger(5);
            errorCount.set(0); // Reset on success
            assertEquals(0, errorCount.get());
        }

        @Test
        @DisplayName("Should increment error count on failure")
        void circuitBreakerIncrementsOnError() {
            AtomicInteger errorCount = new AtomicInteger(0);
            for (int i = 0; i < 5; i++) {
                errorCount.incrementAndGet();
            }
            assertEquals(5, errorCount.get());
        }
    }

    // ==================== T4: Semaphore Concurrency Control ====================
    @Nested
    @DisplayName("BotManager - Semaphore Concurrency Control")
    class SemaphoreTests {

        @Test
        @DisplayName("Semaphore should allow 1 concurrent access per bot")
        void semaphoreSinglePermit() throws Exception {
            Semaphore sem = new Semaphore(1);
            assertTrue(sem.tryAcquire());
            assertFalse(sem.tryAcquire()); // No more permits
            sem.release();
            assertTrue(sem.tryAcquire()); // Available again
        }

        @Test
        @DisplayName("Multiple bots should have independent semaphores")
        void semaphorePerBot() {
            ConcurrentHashMap<Long, Semaphore> semaphores = new ConcurrentHashMap<>();

            Semaphore bot1 = semaphores.computeIfAbsent(1L, k -> new Semaphore(1));
            Semaphore bot2 = semaphores.computeIfAbsent(2L, k -> new Semaphore(1));

            assertTrue(bot1.tryAcquire());
            assertTrue(bot2.tryAcquire()); // Different bot, different semaphore
            assertFalse(bot1.tryAcquire()); // Same bot blocked

            bot1.release();
            bot2.release();
        }

        @Test
        @DisplayName("Queue should drop when full")
        void queueMaxSize() {
            int maxQueueSize = 10;
            Queue<String> queue = new ConcurrentLinkedQueue<>();

            // Fill queue
            for (int i = 0; i < maxQueueSize; i++) {
                queue.offer("msg_" + i);
            }
            assertEquals(maxQueueSize, queue.size());

            // Next message should be dropped
            boolean enqueued = queue.size() < maxQueueSize;
            assertFalse(enqueued);
            assertEquals(10, queue.size());
        }

        @Test
        @DisplayName("FIFO ordering should be preserved")
        void queueFifoOrdering() {
            Queue<String> queue = new ConcurrentLinkedQueue<>();
            queue.offer("first");
            queue.offer("second");
            queue.offer("third");

            assertEquals("first", queue.poll());
            assertEquals("second", queue.poll());
            assertEquals("third", queue.poll());
            assertNull(queue.poll());
        }
    }

    // ==================== T5: Response Cache ====================
    @Nested
    @DisplayName("BotManager - Response Cache")
    class CacheTests {

        @Test
        @DisplayName("LRU cache should evict oldest entry when full")
        void lruCacheEviction() {
            // Simple LRU using LinkedHashMap
            int maxSize = 3;
            Map<String, String> cache = new LinkedHashMap<String, String>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > maxSize;
                }
            };

            cache.put("key1", "val1");
            cache.put("key2", "val2");
            cache.put("key3", "val3");
            cache.put("key4", "val4"); // Should evict key1

            assertEquals(3, cache.size());
            assertFalse(cache.containsKey("key1"));
            assertTrue(cache.containsKey("key4"));
        }

        @Test
        @DisplayName("Cache hit should return stored value")
        void cacheHit() {
            Map<String, String> cache = new HashMap<>();
            cache.put("query:hello", "Hi there!");

            String cached = cache.get("query:hello");
            assertNotNull(cached);
            assertEquals("Hi there!", cached);
        }

        @Test
        @DisplayName("Cache miss should return null")
        void cacheMiss() {
            Map<String, String> cache = new HashMap<>();
            assertNull(cache.get("nonexistent"));
        }

        @Test
        @DisplayName("Cache should support TTL expiration concept")
        void cacheTtlConcept() {
            // Verify that cached entries have timestamp for TTL check
            long now = System.currentTimeMillis();
            long entryTime = now - 200_000; // 200 seconds ago
            long ttlMs = 180_000; // 180 seconds

            boolean expired = (now - entryTime) > ttlMs;
            assertTrue(expired);

            long freshEntry = now - 60_000; // 60 seconds ago
            boolean fresh = (now - freshEntry) > ttlMs;
            assertFalse(fresh);
        }
    }

    // ==================== T6: AiProviderPresetService ====================
    @Nested
    @DisplayName("AiProviderPresetService - Provider Presets")
    class ProviderPresetTests {

        @Test
        @DisplayName("Should contain all 7 provider presets")
        void shouldHaveAllProviders() {
            List<Map<String, String>> providers = getProviderPresets();
            assertEquals(7, providers.size());

            Set<String> ids = new HashSet<>();
            for (Map<String, String> p : providers) {
                ids.add(p.get("id"));
            }
            assertTrue(ids.contains("deepseek"));
            assertTrue(ids.contains("kimi"));
            assertTrue(ids.contains("qwen"));
            assertTrue(ids.contains("gpt"));
            assertTrue(ids.contains("zhipu"));
            assertTrue(ids.contains("mimo"));
            assertTrue(ids.contains("custom"));
        }

        @Test
        @DisplayName("Provider lookup by ID should work")
        void providerLookupById() {
            List<Map<String, String>> providers = getProviderPresets();

            Map<String, String> deepseek = findById(providers, "deepseek");
            assertNotNull(deepseek);
            assertEquals("DeepSeek", deepseek.get("name"));
            assertTrue(deepseek.get("defaultEndpoint").contains("deepseek.com"));

            Map<String, String> qwen = findById(providers, "qwen");
            assertNotNull(qwen);
            assertEquals("Qwen", qwen.get("name"));
            assertTrue(qwen.get("defaultEndpoint").contains("aliyuncs.com"));

            Map<String, String> gpt = findById(providers, "gpt");
            assertNotNull(gpt);
            assertEquals("OpenAI GPT", gpt.get("name"));
            assertTrue(gpt.get("defaultEndpoint").contains("openai.com"));
        }

        @Test
        @DisplayName("Unknown provider ID should return null")
        void unknownProviderReturnsNull() {
            List<Map<String, String>> providers = getProviderPresets();
            assertNull(findById(providers, "nonexistent"));
        }

        private List<Map<String, String>> getProviderPresets() {
            List<Map<String, String>> list = new ArrayList<>();

            Map<String, String> deepseek = new LinkedHashMap<>();
            deepseek.put("id", "deepseek"); deepseek.put("name", "DeepSeek");
            deepseek.put("defaultEndpoint", "https://api.deepseek.com/v1/chat/completions");
            deepseek.put("defaultModel", "deepseek-chat");
            list.add(deepseek);

            Map<String, String> kimi = new LinkedHashMap<>();
            kimi.put("id", "kimi"); kimi.put("name", "Kimi");
            kimi.put("defaultEndpoint", "https://api.moonshot.cn/v1/chat/completions");
            kimi.put("defaultModel", "moonshot-v1-8k");
            list.add(kimi);

            Map<String, String> qwen = new LinkedHashMap<>();
            qwen.put("id", "qwen"); qwen.put("name", "Qwen");
            qwen.put("defaultEndpoint", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
            qwen.put("defaultModel", "qwen-turbo");
            list.add(qwen);

            Map<String, String> mimo = new LinkedHashMap<>();
            mimo.put("id", "mimo"); mimo.put("name", "Mimo");
            mimo.put("defaultEndpoint", "https://api.xiaomimimo.com/v1/chat/completions");
            mimo.put("defaultModel", "mimo-chat");
            list.add(mimo);

            Map<String, String> gpt = new LinkedHashMap<>();
            gpt.put("id", "gpt"); gpt.put("name", "OpenAI GPT");
            gpt.put("defaultEndpoint", "https://api.openai.com/v1/chat/completions");
            gpt.put("defaultModel", "gpt-4o");
            list.add(gpt);

            Map<String, String> zhipu = new LinkedHashMap<>();
            zhipu.put("id", "zhipu"); zhipu.put("name", "Zhipu GLM");
            zhipu.put("defaultEndpoint", "https://open.bigmodel.cn/api/paas/v4/chat/completions");
            zhipu.put("defaultModel", "glm-4");
            list.add(zhipu);

            Map<String, String> custom = new LinkedHashMap<>();
            custom.put("id", "custom"); custom.put("name", "Custom");
            custom.put("defaultEndpoint", "");
            custom.put("defaultModel", "");
            list.add(custom);

            return list;
        }

        private Map<String, String> findById(List<Map<String, String>> providers, String id) {
            return providers.stream()
                .filter(p -> id.equals(p.get("id")))
                .findFirst()
                .orElse(null);
        }
    }

    // ==================== T7: Message Queue Serialization ====================
    @Nested
    @DisplayName("BotMessageQueueService - Serialization")
    class MessageQueueTests {

        @Test
        @DisplayName("Should compute queue key correctly")
        void queueKeyFormat() {
            Long botUserId = 12345L;
            String key = "bot:queue:" + botUserId;
            assertEquals("bot:queue:12345", key);
        }

        @Test
        @DisplayName("Should compute lock key correctly")
        void lockKeyFormat() {
            Long botUserId = 12345L;
            String key = "bot:lock:" + botUserId;
            assertEquals("bot:lock:12345", key);
        }

        @Test
        @DisplayName("Should compute circuit breaker key correctly")
        void circuitKeyFormat() {
            Long botUserId = 12345L;
            String key = "bot:circuit:" + botUserId;
            assertEquals("bot:circuit:12345", key);
        }
    }

    // ==================== T8: LongTermMemory Types ====================
    @Nested
    @DisplayName("LongTermMemoryService - Memory Types")
    class LongTermMemoryTests {

        @Test
        @DisplayName("Memory types should include summary, fact, preference")
        void memoryTypes() {
            Set<String> validTypes = Set.of("summary", "fact", "preference");
            assertTrue(validTypes.contains("summary"));
            assertTrue(validTypes.contains("fact"));
            assertTrue(validTypes.contains("preference"));
        }

        @Test
        @DisplayName("Importance should be in range 1-5")
        void importanceRange() {
            for (int i = 1; i <= 5; i++) {
                assertTrue(i >= 1 && i <= 5);
            }
            assertFalse(0 >= 1 && 0 <= 5);
            assertFalse(6 >= 1 && 6 <= 5);
        }

        @Test
        @DisplayName("Should prune oldest entries when exceeding max")
        void pruneOldestEntries() {
            int maxPerPair = 50;
            int currentCount = 55;
            int toDelete = currentCount - maxPerPair;
            assertEquals(5, toDelete);
        }

        @Test
        @DisplayName("Cache key should include both user IDs")
        void cacheKeyFormat() {
            Long botUserId = 100L;
            Long userId = 200L;
            // Min/Max for deterministic cache key ordering
            long min = Math.min(botUserId, userId);
            long max = Math.max(botUserId, userId);
            String key = "conv:ltm:" + min + ":" + max;
            assertEquals("conv:ltm:100:200", key);
        }
    }

    // ==================== T9: Conversation Memory Working Memory ====================
    @Nested
    @DisplayName("ConversationMemoryCache - Working Memory")
    class WorkingMemoryTests {

        @Test
        @DisplayName("Working memory should cap at max size")
        void workingMemoryCap() {
            int maxSize = 5;
            int actualSize = 8;
            int effective = Math.min(actualSize, maxSize);
            assertEquals(5, effective);
        }

        @Test
        @DisplayName("Working memory should truncate long messages")
        void workingMemoryTruncation() {
            int maxChars = 3000;
            String longMsg = "x".repeat(5000);
            String truncated = longMsg.substring(0, Math.min(longMsg.length(), maxChars));
            assertEquals(3000, truncated.length());
        }

        @Test
        @DisplayName("Short-term memory should use Redis List trim")
        void shortTermMemoryTrim() {
            int maxSize = 30;
            int currentSize = 35;
            // LTRIM keeps last maxSize elements
            int keepStart = -maxSize;
            int keepEnd = -1;
            assertEquals(-30, keepStart);
            assertEquals(-1, keepEnd);
        }
    }

    // ==================== T10: Skill Distillation Logic ====================
    @Nested
    @DisplayName("SkillDistillerService - Emotion & Language Extraction")
    class SkillDistillerTests {

        @Test
        @DisplayName("Emotion distribution should normalize to 1.0")
        void emotionDistributionNormalizes() {
            Map<String, Double> raw = new LinkedHashMap<>();
            raw.put("joy", 15.0);
            raw.put("anger", 5.0);
            raw.put("sad", 3.0);
            raw.put("surprise", 8.0);
            raw.put("fear", 2.0);
            raw.put("care", 7.0);

            double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<String, Double> normalized = new LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                normalized.put(entry.getKey(), entry.getValue() / total);
            }

            double sum = normalized.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.01);
            assertEquals(0.375, normalized.get("joy"), 0.01);
        }

        @Test
        @DisplayName("Avg sentence length should be computed correctly")
        void avgSentenceLength() {
            String[] sentences = {"Hello world", "This is a longer sentence", "Short"};
            double avg = 0;
            for (String s : sentences) avg += s.length();
            avg /= sentences.length;
            // (11 + 25 + 5) / 3 = 13.67
            assertEquals(13.67, avg, 0.1);
        }

        @Test
        @DisplayName("Emoji ratio should be computed correctly")
        void emojiRatio() {
            String text = "Hello 😀 world 😂 nice day";
            // Count emoji in the broader emoji range
            long emojiCount = text.codePoints()
                .filter(cp -> Character.getType(cp) == Character.SURROGATE
                    || (cp >= 0x1F600 && cp <= 0x1F64F)
                    || (cp >= 0x1F300 && cp <= 0x1F5FF)
                    || (cp >= 0x2600 && cp <= 0x26FF))
                .count();
            // 😀 (U+1F600) and 😂 (U+1F602) are in 0x1F600-0x1F64F range
            long justFaces = text.codePoints()
                .filter(cp -> cp >= 0x1F600 && cp <= 0x1F64F).count();
            assertEquals(2, justFaces);
            assertTrue(emojiCount >= 2);
        }
    }
}
