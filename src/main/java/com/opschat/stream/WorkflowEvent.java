package com.opschat.stream;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.Consumer;

@Accessors(chain = true)
@Getter
@Setter
public class WorkflowEvent {

    private WorkflowEventType type;
    private String content;
    private String traceId;

    public WorkflowEvent() {
    }

    public WorkflowEvent(WorkflowEventType type, String content) {
        this.type = type;
        this.content = content;
    }

    public static WorkflowEvent content(String content) {
        return new WorkflowEvent(WorkflowEventType.CONTENT, content);
    }

    public static WorkflowEvent toolCall(String toolName, String description) {
        return new WorkflowEvent(WorkflowEventType.TOOL_CALL, "[" + toolName + "] " + description);
    }

    public static WorkflowEvent toolResult(String toolName, String result) {
        return new WorkflowEvent(WorkflowEventType.TOOL_RESULT, "[" + toolName + "] " + result);
    }

    public static WorkflowEvent searchResult(String result) {
        return new WorkflowEvent(WorkflowEventType.SEARCH_RESULT, result);
    }

    public static WorkflowEvent error(String message) {
        return new WorkflowEvent(WorkflowEventType.ERROR, message);
    }

    public static WorkflowEvent done() {
        return new WorkflowEvent(WorkflowEventType.DONE, "");
    }

    public static WorkflowEvent trace(String message) {
        return new WorkflowEvent(WorkflowEventType.TRACE, message);
    }
}