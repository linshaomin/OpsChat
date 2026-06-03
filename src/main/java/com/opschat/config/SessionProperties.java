package com.opschat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 会话配置属性类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "opschat.session")
public class SessionProperties {

    /**
     * 会话过期时间（小时）
     */
    private int expireHours = 24;

    /**
     * 最大消息窗口大小
     */
    private int maxWindowSize = 30;

    /**
     * 最大token数
     */
    private int maxTokens = 8192;

    /**
     * 摘要压缩阈值
     */
    private float summaryThreshold = 0.7f;
}