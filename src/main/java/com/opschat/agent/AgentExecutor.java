package com.opschat.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.opschat.stream.WorkflowEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent执行引擎（流式模式）
 * 
 * 职责：
 * 1. 创建ReactAgent实例
 * 2. 订阅stream输出
 * 3. 流式返回模型输出
 * 
 * 架构：
 * User Request → AgentExecutor → ReactAgent（官方） → LLM自动循环 → Final Answer
 */
@Slf4j
@Component
public class AgentExecutor {

    private final ReactAgentFactory reactAgentFactory;

    public AgentExecutor(ReactAgentFactory reactAgentFactory) {
        this.reactAgentFactory = reactAgentFactory;
    }

    /**
     * 执行Agent（流式模式）
     * 
     * @param question      用户问题
     * @param history       对话历史
     * @param eventConsumer 流式事件回调
     */
    public void executeStream(String question, List<Map<String, String>> history,
                             Consumer<WorkflowEvent> eventConsumer) {
        log.info("[AgentExecutor] 开始流式执行: {}", question);
        
        try {
            ReactAgent agent = reactAgentFactory.createReactAgent(history);
            
            agent.stream(question).subscribe(
                    output -> {
                        if (output instanceof StreamingOutput streamingOutput) {
                            OutputType type = streamingOutput.getOutputType();
                            
                            // 处理模型输出
                            if (type == OutputType.AGENT_MODEL_STREAMING) {
                                String chunk = streamingOutput.message().getText();
                                if (chunk != null && !chunk.isEmpty()) {
                                    eventConsumer.accept(WorkflowEvent.content(chunk));
                                }
                            } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                log.info("[AgentExecutor] 工具调用完成: {}", output.node());
                            }
                        }
                    },
                    error -> {
                        log.error("[AgentExecutor] 流式执行异常", error);
                        eventConsumer.accept(WorkflowEvent.error(error.getMessage()));
                        eventConsumer.accept(WorkflowEvent.done());
                    },
                    () -> {
                        log.info("[AgentExecutor] 流式执行完成");
                        eventConsumer.accept(WorkflowEvent.done());
                    }
            );
            
        } catch (Exception e) {
            log.error("[AgentExecutor] 流式执行失败", e);
            eventConsumer.accept(WorkflowEvent.error("执行失败: " + e.getMessage()));
            eventConsumer.accept(WorkflowEvent.done());
        }
    }
}