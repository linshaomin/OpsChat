package com.opschat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "opschat.rag.document.chunk")
public class DocumentChunkConfig {

    private int maxSize = 800;
    private int overlap = 100;
}