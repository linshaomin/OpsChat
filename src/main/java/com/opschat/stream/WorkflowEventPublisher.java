package com.opschat.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class WorkflowEventPublisher {

    private final List<Consumer<WorkflowEvent>> consumers = new ArrayList<>();

    public void subscribe(Consumer<WorkflowEvent> consumer) {
        consumers.add(consumer);
        log.debug("新的事件订阅者注册成功，当前订阅者数量: {}", consumers.size());
    }

    public void unsubscribe(Consumer<WorkflowEvent> consumer) {
        consumers.remove(consumer);
        log.debug("事件订阅者取消注册，当前订阅者数量: {}", consumers.size());
    }

    public void publish(WorkflowEvent event) {
        log.trace("发布事件: type={}, content={}", event.getType(), event.getContent());
        for (Consumer<WorkflowEvent> consumer : consumers) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.error("事件消费失败: {}", e.getMessage(), e);
            }
        }
    }

    public void publishContent(String content) {
        publish(WorkflowEvent.content(content));
    }

    public void publishToolCall(String toolName, String description) {
        publish(WorkflowEvent.toolCall(toolName, description));
    }

    public void publishToolResult(String toolName, String result) {
        publish(WorkflowEvent.toolResult(toolName, result));
    }

    public void publishSearchResult(String result) {
        publish(WorkflowEvent.searchResult(result));
    }

    public void publishError(String message) {
        publish(WorkflowEvent.error(message));
    }

    public void publishDone() {
        publish(WorkflowEvent.done());
    }
}