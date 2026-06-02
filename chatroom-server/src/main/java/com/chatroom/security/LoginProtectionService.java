package com.chatroom.security;

import com.chatroom.exception.AccountLockedException;
import com.chatroom.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginProtectionService {

    private static final String RATE_LIMIT_PREFIX = "auth:login:rate:ip:";
    private static final String FAILURE_USER_PREFIX = "auth:login:fail:user:";
    private static final String FAILURE_IP_USER_PREFIX = "auth:login:fail:ip-user:";
    private static final String LOCK_USER_PREFIX = "auth:login:lock:user:";
    private static final String LOCK_IP_USER_PREFIX = "auth:login:lock:ip-user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginProtectionProperties properties;

    private final Cache<String, AtomicInteger> localRateLimitCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    private final Cache<String, AtomicInteger> localFailureCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    private final Cache<String, Boolean> localLockCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    private volatile boolean redisAvailable = true;

    public void checkLoginAllowed(String username, String clientIp) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedIp = normalizeClientIp(clientIp);

        if (isLocked(lockUserKey(normalizedUsername)) || isLocked(lockIpUserKey(normalizedUsername, normalizedIp))) {
            throw new AccountLockedException("登录失败次数过多，账号已被临时锁定，请稍后再试");
        }

        int attemptCount = incrementRateLimit(normalizedIp);
        if (attemptCount > properties.getIpMaxAttempts()) {
            throw new TooManyRequestsException("登录尝试过于频繁，请稍后再试");
        }
    }

    public void onLoginSuccess(String username, String clientIp) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedIp = normalizeClientIp(clientIp);
        clearFailureState(normalizedUsername, normalizedIp);
    }

    public void onLoginFailure(String username, String clientIp) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedIp = normalizeClientIp(clientIp);

        int userFailures = incrementFailure(failureUserKey(normalizedUsername));
        int ipUserFailures = incrementFailure(failureIpUserKey(normalizedUsername, normalizedIp));
        int failureCount = Math.max(userFailures, ipUserFailures);

        if (failureCount >= properties.getFailureThreshold()) {
            lock(lockUserKey(normalizedUsername));
            lock(lockIpUserKey(normalizedUsername, normalizedIp));
            log.warn("Login temporarily locked for username={} ip={}", normalizedUsername, normalizedIp);
        }
    }

    private int incrementRateLimit(String clientIp) {
        String key = rateLimitKey(clientIp);
        if (!isRedisAvailable()) {
            return localRateLimitCache.asMap()
                    .computeIfAbsent(key, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, properties.getIpWindowSeconds(), TimeUnit.SECONDS);
            }
            return count == null ? Integer.MAX_VALUE : count.intValue();
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login rate limit");
            return localRateLimitCache.asMap()
                    .computeIfAbsent(key, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    private int incrementFailure(String key) {
        if (!isRedisAvailable()) {
            return localFailureCache.asMap()
                    .computeIfAbsent(key, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, properties.getLockSeconds(), TimeUnit.SECONDS);
            }
            return count == null ? 0 : count.intValue();
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login failure counter");
            return localFailureCache.asMap()
                    .computeIfAbsent(key, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    private boolean isLocked(String key) {
        if (!isRedisAvailable()) {
            return Boolean.TRUE.equals(localLockCache.getIfPresent(key));
        }

        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login lock state");
            return Boolean.TRUE.equals(localLockCache.getIfPresent(key));
        }
    }

    private void lock(String key) {
        if (!isRedisAvailable()) {
            localLockCache.put(key, Boolean.TRUE);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(key, "1", properties.getLockSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login lock set");
            localLockCache.put(key, Boolean.TRUE);
        }
    }

    private void clearFailureState(String username, String clientIp) {
        String userFailureKey = failureUserKey(username);
        String ipUserFailureKey = failureIpUserKey(username, clientIp);
        String userLockKey = lockUserKey(username);
        String ipUserLockKey = lockIpUserKey(username, clientIp);

        localFailureCache.invalidate(userFailureKey);
        localFailureCache.invalidate(ipUserFailureKey);
        localLockCache.invalidate(userLockKey);
        localLockCache.invalidate(ipUserLockKey);

        if (!isRedisAvailable()) {
            return;
        }

        try {
            stringRedisTemplate.delete(userFailureKey);
            stringRedisTemplate.delete(ipUserFailureKey);
            stringRedisTemplate.delete(userLockKey);
            stringRedisTemplate.delete(ipUserLockKey);
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login clear state");
        }
    }

    private boolean isRedisAvailable() {
        if (!redisAvailable) {
            return false;
        }
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception ex) {
            markRedisUnavailable(ex, "login protection ping");
            return false;
        }
    }

    private void markRedisUnavailable(Exception ex, String scene) {
        redisAvailable = false;
        log.warn("Redis unavailable during {}, falling back to local login protection cache: {}", scene, ex.getMessage());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private String rateLimitKey(String clientIp) {
        return RATE_LIMIT_PREFIX + clientIp;
    }

    private String failureUserKey(String username) {
        return FAILURE_USER_PREFIX + username;
    }

    private String failureIpUserKey(String username, String clientIp) {
        return FAILURE_IP_USER_PREFIX + clientIp + ":" + username;
    }

    private String lockUserKey(String username) {
        return LOCK_USER_PREFIX + username;
    }

    private String lockIpUserKey(String username, String clientIp) {
        return LOCK_IP_USER_PREFIX + clientIp + ":" + username;
    }
}
