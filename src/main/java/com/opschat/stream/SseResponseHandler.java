package com.opschat.stream;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class SseResponseHandler {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onTimeout(() -> log.debug("SSE 连接超时"));
        emitter.onError(e -> log.error("SSE 连接异常: {}", e.getMessage()));
        return emitter;
    }

    public Consumer<WorkflowEvent> createEventConsumer(SseEmitter emitter) {
        return event -> {
            try {
                switch (event.getType()) {
                    case CONTENT -> sendContent(emitter, event.getContent(), 0);
                    case TOOL_CALL -> sendToolCall(emitter, event.getContent());
                    case TOOL_RESULT -> sendToolResult(emitter, event.getContent());
                    case SEARCH_RESULT -> sendSearchResult(emitter, event.getContent());
                    case ERROR -> sendError(emitter, event.getContent());
                    case DONE -> sendDone(emitter);
                    case TRACE -> log.trace("{}", event.getContent());
                }
            } catch (IOException e) {
                log.error("SSE 发送失败: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        };
    }

    public void sendContent(SseEmitter emitter, String content, int index) throws IOException {
        if (content != null && !content.isEmpty()) {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(JSONUtil.toJsonStr(Map.of(
                            "type", "content",
                            "content", content,
                            "index", index
                    )), MediaType.APPLICATION_JSON));
        }
    }

    public void sendToolCall(SseEmitter emitter, String content) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "tool_call",
                        "content", content
                )), MediaType.APPLICATION_JSON));
    }

    public void sendToolResult(SseEmitter emitter, String content) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "tool_result",
                        "content", content
                )), MediaType.APPLICATION_JSON));
    }

    public void sendSearchResult(SseEmitter emitter, String content) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "search_result",
                        "content", content
                )), MediaType.APPLICATION_JSON));
    }

    public void sendError(SseEmitter emitter, String message) throws IOException {
        emitter.send(SseEmitter.event()
                .name("error")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "error",
                        "content", message
                )), MediaType.APPLICATION_JSON));
    }

    public void sendDone(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event()
                .name("done")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "done"
                )), MediaType.APPLICATION_JSON));
        emitter.complete();
    }

    public void sendSessionId(SseEmitter emitter, String sessionId) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(JSONUtil.toJsonStr(Map.of(
                        "type", "sessionId",
                        "content", sessionId
                )), MediaType.APPLICATION_JSON));
    }
}