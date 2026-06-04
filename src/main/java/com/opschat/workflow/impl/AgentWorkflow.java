package com.opschat.workflow.impl;

import com.opschat.agent.AgentExecutor;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.WorkflowStrategy;
import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent工作流
 * 统一的复杂任务执行入口，使用官方 ReactAgent 进行多步推理和工具调用
 * 
 * 职责：
 * - 接收复杂问题
 * - 调用 AgentExecutor 执行推理
 * - 返回最终结果
 */
@Slf4j
@Service
public class AgentWorkflow implements WorkflowStrategy {

    private final AgentExecutor agentExecutor;

    public AgentWorkflow(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    @Override
    public WorkflowType getType() {
        return WorkflowType.AGENT;
    }

    /**
     * 流式执行Agent工作流
     * 调用 AgentExecutor 进行多轮工具调用和推理
     */
    @Override
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[AgentWorkflow] 执行流式Agent工作流: {}", question);
        
        // 调用 AgentExecutor 执行
        agentExecutor.executeStream(question, history, eventConsumer);
    }
}