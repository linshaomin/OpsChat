package com.opschat.workflow;

import com.opschat.router.AgentGateRouter;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.impl.AgentWorkflow;
import com.opschat.workflow.impl.ChatWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流编排器（仅流式模式）
 * 统一 React Agent 驱动架构的核心编排组件
 * 
 * 架构：
 * - 简单问题：直接走 ChatWorkflow（LLM直接回答）
 * - 复杂问题：进入 AgentWorkflow（React Agent多步推理）
 * 
 * Router 仅作为轻量 Gate，不做意图分类
 */
@Slf4j
@Component
public class WorkflowOrchestrator {

    /**
     * 工作流策略映射
     */
    private final Map<WorkflowType, WorkflowStrategy> workflowStrategyMap = new EnumMap<>(WorkflowType.class);

    /**
     * Agent Gate 路由器（轻量门限判断）
     */
    private final AgentGateRouter agentGateRouter;

    /**
     * 构造函数
     * @param chatWorkflow 聊天工作流（简单问题）
     * @param agentWorkflow Agent工作流（复杂问题）
     * @param agentGateRouter Agent门限路由器
     */
    public WorkflowOrchestrator(ChatWorkflow chatWorkflow,
                                 AgentWorkflow agentWorkflow,
                                 AgentGateRouter agentGateRouter) {
        this.agentGateRouter = agentGateRouter;
        registerWorkflow(chatWorkflow);
        registerWorkflow(agentWorkflow);
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        log.info("[WorkflowOrchestrator] 初始化完成，已注册 {} 个工作流: {}", 
                workflowStrategyMap.size(), workflowStrategyMap.keySet());
    }

    /**
     * 注册工作流
     * @param workflow 工作流策略
     */
    private void registerWorkflow(WorkflowStrategy workflow) {
        workflowStrategyMap.put(workflow.getType(), workflow);
    }

    /**
     * 流式执行工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[WorkflowOrchestrator] 执行流式工作流: {}", question);

        // 使用轻量 Gate 判断是否进入 Agent
        AgentGateRouter.GateResult gateResult = agentGateRouter.shouldUseAgent(question);
        log.info("[WorkflowOrchestrator] Gate 判断结果: useAgent={}, confidence={}, reason={}",
                gateResult.isUseAgent(), gateResult.getConfidence(), gateResult.getReason());

        // 根据 Gate 判断结果选择工作流
        WorkflowType workflowType = gateResult.isUseAgent() ? WorkflowType.AGENT : WorkflowType.CHAT;

        WorkflowStrategy strategy = workflowStrategyMap.get(workflowType);
        if (strategy == null) {
            log.error("[WorkflowOrchestrator] 未找到工作流策略: {}", workflowType);
            eventConsumer.accept(WorkflowEvent.error("未找到工作流策略: " + workflowType));
            return;
        }

        try {
            strategy.executeStream(question, history, eventConsumer);
        } catch (Exception e) {
            log.error("[WorkflowOrchestrator] 工作流执行失败: {}", workflowType, e);
            // Agent失败时 fallback 到 Chat
            if (workflowType == WorkflowType.AGENT) {
                log.warn("[WorkflowOrchestrator] Agent 失败，fallback 到 Chat");
                WorkflowStrategy chatStrategy = workflowStrategyMap.get(WorkflowType.CHAT);
                if (chatStrategy != null) {
                    try {
                        chatStrategy.executeStream(question, history, eventConsumer);
                        return;
                    } catch (Exception ex) {
                        log.error("[WorkflowOrchestrator] Chat fallback 也失败", ex);
                    }
                }
            }
            eventConsumer.accept(WorkflowEvent.error("处理失败: " + e.getMessage()));
        }
    }
}