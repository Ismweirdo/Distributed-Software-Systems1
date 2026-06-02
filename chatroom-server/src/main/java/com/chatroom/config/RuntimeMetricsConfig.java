package com.chatroom.config;

import com.chatroom.service.BotManager;
import com.chatroom.websocket.OnlineStatusManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class RuntimeMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final OnlineStatusManager onlineStatusManager;
    private final BotManager botManager;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("chatroom.online.users", onlineStatusManager, OnlineStatusManager::getOnlineUserCount)
                .description("Current online user count")
                .register(meterRegistry);

        Gauge.builder("chatroom.bot.queue.total", botManager, manager -> {
                    Object total = manager.getQueueStats().get("totalQueued");
                    return total instanceof Number ? ((Number) total).doubleValue() : 0D;
                })
                .description("Total queued bot messages")
                .register(meterRegistry);

        Gauge.builder("chatroom.bot.active_mode.enabled", botManager, manager -> {
                    Object activeBots = manager.getAllActiveModeInfos().get("activeBots");
                    if (!(activeBots instanceof List<?> list)) {
                        return 0D;
                    }
                    return list.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .filter(item -> Boolean.TRUE.equals(item.get("enabled")))
                            .count();
                })
                .description("Enabled bot active-mode count")
                .register(meterRegistry);
    }
}
