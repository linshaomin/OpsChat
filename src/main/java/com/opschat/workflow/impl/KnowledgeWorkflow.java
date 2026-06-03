package com.opschat.workflow.impl;

import com.alibaba.dashscope.common.Message;
import com.opschat.llm.LlmService;
import com.opschat.llm.PromptTemplateService;
import com.opschat.service.RagService;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.WorkflowStrategy;
import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 知识库工作流
 * RAG检索+LLM总结，处理技术文档问答
 */
@Slf4j
@Service
public class KnowledgeWorkflow implements WorkflowStrategy {

    /**
     * RAG服务
     */
    private final RagService ragService;

    /**
     * LLM服务
     */
    private final LlmService llmService;

    /**
     * 提示词模板服务
     */
    private final PromptTemplateService promptTemplate;

    /**
     * 构造函数
     * @param ragService RAG服务
     * @param llmService LLM服务
     * @param promptTemplate 提示词模板服务
     */
    public KnowledgeWorkflow(RagService ragService, LlmService llmService, PromptTemplateService promptTemplate) {
        this.ragService = ragService;
        this.llmService = llmService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 获取工作流类型
     * @return RAG类型
     */
    @Override
    public WorkflowType getType() {
        return WorkflowType.RAG;
    }

    /**
     * 同步执行知识库工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @return 回答内容
     */
    @Override
    public String execute(String question, List<Map<String, String>> history) {
        log.info("[KnowledgeWorkflow] 执行知识库工作流");
        String context = ragService.query(question);
        List<Message> messages = promptTemplate.buildRagPrompt(question, context);
        return llmService.chat(messages);
    }

    /**
     * 流式执行知识库工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    @Override
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[KnowledgeWorkflow] 执行流式知识库工作流");
        try {
            // 调用RAG服务进行流式检索
            ragService.queryStream(question, eventConsumer);
        } catch (Exception e) {
            log.error("[KnowledgeWorkflow] 流式执行失败", e);
            eventConsumer.accept(WorkflowEvent.error(e.getMessage()));
        }
    }
}