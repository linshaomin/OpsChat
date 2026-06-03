package com.opschat.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import com.opschat.config.MilvusProperties;
import com.opschat.constant.MilvusConstants;
import com.opschat.dto.DocumentChunk;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class VectorIndexService {

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private DocumentChunkService chunkService;

    @Autowired
    private MilvusProperties milvusProperties;

    @Value("${opschat.rag.file.upload.path:./uploads}")
    private String uploadPath;

    public IndexingResult indexDirectory(String directoryPath) {
        IndexingResult result = new IndexingResult();
        result.setStartTime(LocalDateTime.now());

        try {
            String targetPath = (directoryPath != null && !directoryPath.trim().isEmpty())
                    ? directoryPath : uploadPath;

            Path dirPath = Paths.get(targetPath).normalize();
            File directory = dirPath.toFile();

            if (!directory.exists() || !directory.isDirectory()) {
                throw new IllegalArgumentException("目录不存在或不是有效目录: " + targetPath);
            }

            result.setDirectoryPath(directory.getAbsolutePath());

            File[] files = directory.listFiles((dir, name) ->
                    name.endsWith(".txt") || name.endsWith(".md")
            );

            if (files == null || files.length == 0) {
                log.warn("目录中没有找到支持的文件: {}", targetPath);
                result.setTotalFiles(0);
                result.setSuccess(true);
                result.setEndTime(LocalDateTime.now());
                return result;
            }

            result.setTotalFiles(files.length);
            log.info("开始索引目录: {}, 找到 {} 个文件", targetPath, files.length);

            for (File file : files) {
                try {
                    indexSingleFile(file.getAbsolutePath());
                    result.incrementSuccessCount();
                    log.info("✓ 文件索引成功: {}", file.getName());
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.getAbsolutePath(), e.getMessage());
                    log.error("✗ 文件索引失败: {}", file.getName(), e);
                }
            }

            result.setSuccess(result.getFailCount() == 0);
            result.setEndTime(LocalDateTime.now());

            log.info("目录索引完成: 总数={}, 成功={}, 失败={}",
                    result.getTotalFiles(), result.getSuccessCount(), result.getFailCount());

            return result;

        } catch (Exception e) {
            log.error("索引目录失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        }
    }

    public void indexSingleFile(String filePath) throws Exception {
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        log.info("开始索引文件: {}", path);

        String content = Files.readString(path);
        log.info("读取文件: {}, 内容长度: {} 字符", path, content.length());

        deleteExistingData(path.toString());

        List<DocumentChunk> chunks = chunkService.chunkDocument(content, path.toString());
        log.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            try {
                List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());

                Map<String, Object> metadata = buildMetadata(path.toString(), chunk, chunks.size());

                insertToMilvus(chunk.getContent(), vector, metadata, chunk.getChunkIndex());

                log.info("✓ 分片 {}/{} 索引成功", i + 1, chunks.size());

            } catch (Exception e) {
                log.error("✗ 分片 {}/{} 索引失败", i + 1, chunks.size(), e);
                throw new RuntimeException("分片索引失败: " + e.getMessage(), e);
            }
        }

        log.info("文件索引完成: {}, 共 {} 个分片", filePath, chunks.size());
    }

    private void deleteExistingData(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace(File.separator, "/");

            String expr = String.format("metadata[\"_source\"] == \"%s\"", normalizedPath);

            log.info("准备删除旧数据，路径: {}, 表达式: {}", normalizedPath, expr);

            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );

            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                log.warn("加载 collection 失败: {}", loadResponse.getMessage());
                return;
            }

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            R<MutationResult> deleteResponse = milvusClient.delete(deleteParam);

            if (deleteResponse.getStatus() != 0) {
                log.warn("删除旧数据失败: {}", deleteResponse.getMessage());
            } else {
                log.info("旧数据删除成功: {}", normalizedPath);
            }

        } catch (Exception e) {
            log.warn("删除旧数据异常: {}", e.getMessage());
        }
    }

    private void insertToMilvus(String content, List<Float> vector, Map<String, Object> metadata, int chunkIndex) {
        try {
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );

            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                throw new RuntimeException("加载 collection 失败: " + loadResponse.getMessage());
            }

            String source = (String) metadata.get("_source");
            if (source == null) {
                source = "unknown";
            }
            String id = UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes()).toString();

            // 记录插入信息
            log.debug("准备插入数据 - id: {}, content_length: {}, vector_dim: {}", 
                    id, content.length(), vector.size());

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", Collections.singletonList(id)));
            fields.add(new InsertParam.Field("content", Collections.singletonList(content)));
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

            Gson gson = new Gson();
            // 使用 JsonObject 方式插入 JSON 字段（与 SuperBizAgent 一致）
            com.google.gson.JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
            log.debug("Metadata JSON: {}", metadataJson);
            fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            R<MutationResult> insertResponse = milvusClient.insert(insertParam);
            
            // 在新版本 SDK 中，成功时 getMessage() 可能会抛出 NullPointerException
            // 所以只在状态不为 0 时才获取错误消息
            if (insertResponse.getStatus() != 0) {
                String errorMsg = null;
                try {
                    errorMsg = insertResponse.getMessage();
                } catch (Exception e) {
                    log.debug("获取错误消息失败", e);
                }
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "未知错误";
                }
                throw new RuntimeException("插入向量失败: " + errorMsg);
            }

            log.info("向量插入成功: id={}, source={}, chunk={}", id, source, chunkIndex);

        } catch (Exception e) {
            log.error("插入向量到 Milvus 失败", e);
            throw e;
        }
    }

    private Map<String, Object> buildMetadata(String filePath, DocumentChunk chunk, int totalChunks) {
        Map<String, Object> metadata = new HashMap<>();
        
        // 标准化路径：使用统一的路径分隔符（正斜杠）用于存储，确保跨平台一致性
        Path path = Paths.get(filePath).normalize();
        String normalizedPath = path.toString().replace(File.separator, "/");
        
        // 文件信息
        Path fileName = path.getFileName();
        String fileNameStr = fileName != null ? fileName.toString() : "";
        String extension = "";
        int dotIndex = fileNameStr.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileNameStr.substring(dotIndex);
        }
        
        metadata.put("_source", normalizedPath);
        metadata.put("_extension", extension);
        metadata.put("_file_name", fileNameStr);
        
        // 分片信息
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("totalChunks", totalChunks);
        
        // 标题信息
        if (chunk.getSource() != null && !chunk.getSource().isEmpty()) {
            metadata.put("title", chunk.getSource());
        }
        
        return metadata;
    }

    @Getter
    @Setter
    public static class IndexingResult {
        private String directoryPath;
        private int totalFiles;
        private int successCount;
        private int failCount;
        private boolean success;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<FailedFile> failedFiles = new ArrayList<>();

        public void incrementSuccessCount() {
            this.successCount++;
        }

        public void incrementFailCount() {
            this.failCount++;
        }

        public void addFailedFile(String path, String reason) {
            this.failedFiles.add(new FailedFile(path, reason));
        }

        @Getter
        @Setter
        public static class FailedFile {
            private String path;
            private String reason;

            public FailedFile(String path, String reason) {
                this.path = path;
                this.reason = reason;
            }
        }
    }
}