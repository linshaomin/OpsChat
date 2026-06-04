package com.opschat.workflow.impl;

import com.alibaba.dashscope.common.Message;
import com.opschat.llm.LlmService;
import com.opschat.llm.PromptTemplateService;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.WorkflowStrategy;
import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天工作流
 * 直接使用LLM回答，处理闲聊、问候、能力咨询等
 */
@Slf4j
@Service
public class ChatWorkflow implements WorkflowStrategy {

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
     * @param llmService LLM服务
     * @param promptTemplate 提示词模板服务
     */
    public ChatWorkflow(LlmService llmService, PromptTemplateService promptTemplate) {
        this.llmService = llmService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 获取工作流类型
     * @return CHAT类型
     */
    @Override
    public WorkflowType getType() {
        return WorkflowType.CHAT;
    }

    /**
     * 流式执行聊天工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    @Override
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[ChatWorkflow] 执行流式聊天工作流");
        try {
            // 构建提示词
            List<Message> messages = promptTemplate.buildChatPrompt(history, question);
            
            // 流式调用LLM
            llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
            
            // 发送完成事件
            eventConsumer.accept(WorkflowEvent.done());
        } catch (Exception e) {
            log.error("[ChatWorkflow] 流式执行失败", e);
            eventConsumer.accept(WorkflowEvent.error(e.getMessage()));
        }
    }
}