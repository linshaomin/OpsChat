package com.opschat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "opschat.rag.milvus")
public class MilvusProperties {

    private String host = "localhost";
    private int port = 19530;
    private String collection = "opschat_docs";
    private int timeout = 5000;
    private String username = "";
    private String password = "";
    private String database = "default";
}