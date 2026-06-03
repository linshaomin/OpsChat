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
 * React工作流
 * 多步骤推理分析，处理根因定位、故障分析等复杂问题
 */
@Slf4j
@Service
public class ReactWorkflow implements WorkflowStrategy {

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
    public ReactWorkflow(LlmService llmService, PromptTemplateService promptTemplate) {
        this.llmService = llmService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 获取工作流类型
     * @return REACT类型
     */
    @Override
    public WorkflowType getType() {
        return WorkflowType.REACT;
    }

    /**
     * 同步执行React工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @return 回答内容
     */
    @Override
    public String execute(String question, List<Map<String, String>> history) {
        log.info("[ReactWorkflow] 执行React工作流");
        List<Message> messages = promptTemplate.buildReactPrompt(history, question);
        return llmService.chat(messages);
    }

    /**
     * 流式执行React工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    @Override
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[ReactWorkflow] 执行流式React工作流");
        try {
            // 发送追踪事件
            eventConsumer.accept(WorkflowEvent.trace("开始多步骤推理分析"));
            
            // 构建React提示词
            List<Message> messages = promptTemplate.buildReactPrompt(history, question);
            
            // 流式调用LLM进行推理
            llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
            
            // 发送完成事件
            eventConsumer.accept(WorkflowEvent.done());
        } catch (Exception e) {
            log.error("[ReactWorkflow] 流式执行失败", e);
            eventConsumer.accept(WorkflowEvent.error(e.getMessage()));
        }
    }
}