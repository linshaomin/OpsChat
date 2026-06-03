package com.opschat.config;

import io.milvus.client.MilvusServiceClient;
import com.opschat.client.MilvusClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Slf4j
@Configuration
public class MilvusConfig {

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("正在初始化 Milvus 客户端...");
        milvusClient = milvusClientFactory.createClient();
        log.info("Milvus 客户端初始化完成");
        return milvusClient;
    }

    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            log.info("正在关闭 Milvus 客户端连接...");
            milvusClient.close();
            log.info("Milvus 客户端连接已关闭");
        }
    }
}