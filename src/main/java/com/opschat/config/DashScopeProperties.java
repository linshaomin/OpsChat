package com.opschat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "opschat.ai.dashscope")
public class DashScopeProperties {

    private String apiKey;
    private String embeddingModel = "text-embedding-v4";
    private ChatOptions chat = new ChatOptions();

    @Data
    public static class ChatOptions {
        private String model = "qwen-turbo";
        private int timeout = 120000;
        private float temperature = 0.7f;
        private int maxTokens = 2048;
    }
}