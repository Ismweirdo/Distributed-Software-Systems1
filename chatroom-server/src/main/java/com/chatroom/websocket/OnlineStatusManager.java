package com.chatroom.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineStatusManager {

    private static final String USER_SESSION_PREFIX = "presence:user:";
    private static final String SESSION_USER_PREFIX = "presence:session:";
    private static final long SESSION_TTL_HOURS = 12;

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<Long, Set<String>> localOnlineUsers = new ConcurrentHashMap<>();

    private volatile boolean redisAvailable = true;

    public boolean userOnline(Long userId, String sessionId) {
        if (!isRedisAvailable()) {
            Set<String> sessions = localOnlineUsers.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
            boolean wasOffline = sessions.isEmpty();
            sessions.add(sessionId);
            return wasOffline;
        }

        String userKey = USER_SESSION_PREFIX + userId;
        String sessionKey = SESSION_USER_PREFIX + sessionId;
        Long before = stringRedisTemplate.opsForSet().size(userKey);
        stringRedisTemplate.opsForSet().add(userKey, sessionId);
        stringRedisTemplate.expire(userKey, SESSION_TTL_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForValue().set(sessionKey, String.valueOf(userId), SESSION_TTL_HOURS, TimeUnit.HOURS);
        return before == null || before == 0;
    }

    public boolean userOffline(Long userId, String sessionId) {
        if (!isRedisAvailable()) {
            Set<String> sessions = localOnlineUsers.get(userId);
            if (sessions == null) {
                return false;
            }
            sessions.remove(sessionId);
            if (!sessions.isEmpty()) {
                return false;
            }
            localOnlineUsers.remove(userId);
            return true;
        }

        String userKey = USER_SESSION_PREFIX + userId;
        String sessionKey = SESSION_USER_PREFIX + sessionId;
        stringRedisTemplate.opsForSet().remove(userKey, sessionId);
        stringRedisTemplate.delete(sessionKey);
        Long remaining = stringRedisTemplate.opsForSet().size(userKey);
        if (remaining == null || remaining == 0) {
            stringRedisTemplate.delete(userKey);
            return true;
        }
        stringRedisTemplate.expire(userKey, SESSION_TTL_HOURS, TimeUnit.HOURS);
        return false;
    }

    public boolean isOnline(Long userId) {
        if (!isRedisAvailable()) {
            Set<String> sessions = localOnlineUsers.get(userId);
            return sessions != null && !sessions.isEmpty();
        }
        Long size = stringRedisTemplate.opsForSet().size(USER_SESSION_PREFIX + userId);
        return size != null && size > 0;
    }

    public int getOnlineUserCount() {
        if (!isRedisAvailable()) {
            return (int) localOnlineUsers.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).count();
        }
        Set<String> keys = stringRedisTemplate.keys(USER_SESSION_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        return Math.toIntExact(keys.stream()
                .filter(key -> {
                    Long size = stringRedisTemplate.opsForSet().size(key);
                    return size != null && size > 0;
                })
                .count());
    }

    private boolean isRedisAvailable() {
        if (!redisAvailable) {
            return false;
        }
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception ex) {
            redisAvailable = false;
            log.warn("Redis unavailable, falling back to local online status state");
            return false;
        }
    }
}
