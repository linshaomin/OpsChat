package com.opschat.stream;

/**
 * 工作流事件类型枚举
 */
public enum WorkflowEventType {
    CONTENT("content", "内容块"),
    TOOL_CALL("tool_call", "工具调用"),
    TOOL_RESULT("tool_result", "工具结果"),
    SEARCH_RESULT("search_result", "搜索结果"),
    ERROR("error", "错误"),
    DONE("done", "完成"),
    TRACE("trace", "追踪");

    /**
     * 类型标识
     */
    private final String type;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 构造函数
     * @param type 类型标识
     * @param description 描述
     */
    WorkflowEventType(String type, String description) {
        this.type = type;
        this.description = description;
    }
}