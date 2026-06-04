package com.opschat.workflow;

/**
 * 工作流类型枚举
 * 统一 React Agent 驱动架构：
 * - CHAT: 简单问题直接回答（闲聊、问候、能力咨询等）
 * - AGENT: 复杂问题进入 React Agent（需要工具调用、多步推理）
 */
public enum WorkflowType {
    CHAT("直接回答", "简单问题直接由LLM回答，如问候、告别、能力咨询等"),
    AGENT("Agent推理", "复杂问题进入React Agent进行多步推理和工具调用");

    /**
     * 类型名称
     */
    private final String name;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 构造函数
     * @param name 名称
     * @param description 描述
     */
    WorkflowType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 获取名称
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取描述
     * @return 描述
     */
    public String getDescription() {
        return description;
    }
}