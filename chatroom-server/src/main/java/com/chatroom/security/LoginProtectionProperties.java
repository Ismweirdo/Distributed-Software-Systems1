package com.chatroom.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.security.login-protection")
public class LoginProtectionProperties {

    /**
     * IP 维度滑动窗口时长，单位秒。
     */
    private long ipWindowSeconds = 60;

    /**
     * 单个 IP 在窗口内允许的最大登录尝试次数。
     */
    private int ipMaxAttempts = 10;

    /**
     * 账号或账号+IP 组合连续失败阈值。
     */
    private int failureThreshold = 5;

    /**
     * 触发锁定后的持续时间，单位秒。
     */
    private long lockSeconds = 900;
}
