package com.opschat.service;

import com.opschat.config.DocumentChunkConfig;
import com.opschat.dto.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DocumentChunkServiceTest {

    @Autowired
    private DocumentChunkService chunkService;

    @Test
    public void testChunkDocument() {
        String content = """
# CPU使用率过高告警处理方案

## 告警名称
- **告警名**: `HighCPUUsage`
- **告警级别**: 严重
- **触发条件**: CPU使用率持续5分钟超过80%

## 问题描述
当服务器或容器的CPU使用率持续超过80%时，可能导致：
- 应用响应变慢
- 请求超时增加
- 系统负载过高

## 排查步骤
### 步骤1: 获取当前时间
**工具**: `get_current_time`
### 步骤2: 查询系统日志
**工具**: `query_logs`

## 常见原因分析
### 原因1: 死循环或无限递归
特征：单个进程CPU占用接近100%
### 原因2: 流量突增
特征：多个进程CPU使用率均匀升高
""";

        List<DocumentChunk> chunks = chunkService.chunkDocument(content, "test.md");

        System.out.println("=== 分片结果 ===");
        System.out.println("总分片数: " + chunks.size());
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            System.out.println("\n--- 分片 " + (i + 1) + " ---");
            System.out.println("标题(source): " + chunk.getSource());
            System.out.println("内容长度: " + chunk.getContent().length());
            System.out.println("内容预览(前100字符): " + chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())));
            System.out.println("内容是否包含标题: " + (chunk.getContent().contains("#") ? "是" : "否"));
        }
    }
}