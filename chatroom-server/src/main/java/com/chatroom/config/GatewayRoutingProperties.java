package com.chatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.gateway")
public class GatewayRoutingProperties {

    /**
     * 是否启用网关路由适配配置。
     */
    private boolean enabled = false;

    /**
     * 网关暴露给外部的基础地址，例如 https://chat.example.com。
     */
    private String externalBaseUrl = "";

    /**
     * 统一 API 前缀，为后续 API Gateway 重写路由预留。
     */
    private String apiPrefix = "/api";

    /**
     * WebSocket 统一入口前缀。
     */
    private String websocketPrefix = "/ws";

    /**
     * 公共文件资源前缀。
     */
    private String publicFilesPrefix = "/api/files/public";
}
