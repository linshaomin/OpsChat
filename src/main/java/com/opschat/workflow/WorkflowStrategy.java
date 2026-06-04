package com.opschat.workflow;

import com.opschat.stream.WorkflowEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流策略接口
 * 定义工作流执行的标准方法（仅流式）
 */
public interface WorkflowStrategy {

    /**
     * 获取工作流类型
     * @return 工作流类型
     */
    WorkflowType getType();

    /**
     * 流式执行工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer);
}