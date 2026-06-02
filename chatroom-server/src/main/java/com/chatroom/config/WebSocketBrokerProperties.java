package com.chatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.websocket")
public class WebSocketBrokerProperties {

    /**
     * simple: Spring in-memory broker
     * relay: external STOMP broker relay (recommended for multi-node deployment)
     */
    private String mode = "simple";

    private final Relay relay = new Relay();

    private String userDestinationBroadcast = "/topic/unresolved-user-destination";
    private String userRegistryBroadcast = "/topic/simp-user-registry";

    @Data
    public static class Relay {
        private String host = "localhost";
        private Integer port = 61613;
        private String clientLogin = "guest";
        private String clientPasscode = "guest";
        private String systemLogin = "guest";
        private String systemPasscode = "guest";
        private String virtualHost = "/";
        private Long systemHeartbeatSendInterval = 10000L;
        private Long systemHeartbeatReceiveInterval = 10000L;
    }
}
