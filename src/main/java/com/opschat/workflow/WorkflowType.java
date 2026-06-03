package com.opschat.workflow;

/**
 * 工作流类型枚举
 * 五分类体系：
 * - CHAT: 闲聊对话/能力咨询
 * - TOOL: 单步系统查询
 * - RAG: 知识库问答
 * - REACT: 多步骤推理分析
 * - CLARIFY: 需要澄清
 */
public enum WorkflowType {
    CHAT("闲聊对话", "直接LLM回答，处理问候、告别、能力咨询等"),
    TOOL("单步查询", "调用工具查询，如日志、监控、订单等"),
    RAG("知识库问答", "检索文档并总结，如SOP、架构文档"),
    REACT("智能分析", "多步骤推理分析，如根因定位"),
    CLARIFY("意图澄清", "信息不足，需要用户补充");

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