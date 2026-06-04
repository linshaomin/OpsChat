package com.opschat.agent.tool;

import com.opschat.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具
 * 将 RAG 能力作为 Tool 接入，使用 Spring AI @Tool 注解规范
 */
@Slf4j
@Component
public class KnowledgeBaseTool {

    private final RagService ragService;

    public KnowledgeBaseTool(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 检索知识库文档
     * @param query 查询关键词或问题
     * @return 检索结果
     */
    @Tool(description = "检索知识库文档")
    public String searchKnowledgeBase(String query) {
        log.info("[KnowledgeBaseTool] 执行知识库检索: {}", query);
        
        if (query == null || query.trim().isEmpty()) {
            return "错误：查询参数不能为空";
        }

        try {
            String result = ragService.query(query);
            log.debug("[KnowledgeBaseTool] 检索结果长度: {} 字符", result.length());
            
            if (result == null || result.isEmpty()) {
                return "知识库中未找到相关信息";
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] 检索失败", e);
            return "知识库检索失败: " + e.getMessage();
        }
    }
}