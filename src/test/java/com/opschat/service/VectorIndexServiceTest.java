package com.opschat.service;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * 向量索引服务单元测试 - 验证数据格式
 */
class VectorIndexServiceTest {

    @Test
    void testMetadataFormat() {
        // 模拟 metadata 数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("_source", "uploads/cpu_high_usage.md");
        metadata.put("chunk_index", 0);
        metadata.put("total_chunks", 5);
        metadata.put("source_title", "CPU使用率过高告警处理方案");

        Gson gson = new Gson();
        String metadataStr = gson.toJson(metadata);
        
        System.out.println("=== Metadata JSON ===");
        System.out.println(metadataStr);
        System.out.println("长度: " + metadataStr.length());
        
        // 验证不是null
        assert metadataStr != null;
        assert !metadataStr.isEmpty();
        
        // 验证JSON格式正确
        assert metadataStr.startsWith("{");
        assert metadataStr.endsWith("}");
    }

    @Test
    void testIdGeneration() {
        String source = "uploads/cpu_high_usage.md";
        int chunkIndex = 0;
        
        String id = UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes()).toString();
        
        System.out.println("=== ID生成 ===");
        System.out.println("Source: " + source);
        System.out.println("ChunkIndex: " + chunkIndex);
        System.out.println("Generated ID: " + id);
        
        assert id != null;
        assert id.length() == 36; // UUID标准长度
    }

    @Test
    void testVectorFormat() {
        // 生成一个1024维的测试向量
        List<Float> vector = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 1024; i++) {
            vector.add(random.nextFloat());
        }
        
        System.out.println("=== 向量格式 ===");
        System.out.println("向量维度: " + vector.size());
        System.out.println("第一个元素: " + vector.get(0));
        System.out.println("最后一个元素: " + vector.get(1023));
        
        assert vector.size() == 1024;
        assert !vector.contains(null);
    }

    @Test
    void testAllFields() {
        // 模拟完整的插入数据
        String id = UUID.randomUUID().toString();
        String content = "# CPU使用率过高告警处理方案\n\n## 告警名称\n- **告警名**: `HighCPUUsage`";
        List<Float> vector = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 1024; i++) {
            vector.add(random.nextFloat());
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("_source", "uploads/cpu_high_usage.md");
        metadata.put("chunk_index", 0);
        metadata.put("total_chunks", 5);
        metadata.put("source_title", "CPU使用率过高告警处理方案");
        
        Gson gson = new Gson();
        String metadataStr = gson.toJson(metadata);
        
        System.out.println("=== 完整字段测试 ===");
        System.out.println("ID: " + id + " (长度: " + id.length() + ")");
        System.out.println("Content: " + content.substring(0, Math.min(50, content.length())) + "... (长度: " + content.length() + ")");
        System.out.println("Vector: " + vector.size() + " 维");
        System.out.println("Metadata: " + metadataStr.substring(0, Math.min(100, metadataStr.length())) + "... (长度: " + metadataStr.length() + ")");
        
        // 验证所有字段都不为空
        assert id != null && !id.isEmpty();
        assert content != null && !content.trim().isEmpty();
        assert vector != null && !vector.isEmpty();
        assert metadataStr != null && !metadataStr.isEmpty();
    }
}
