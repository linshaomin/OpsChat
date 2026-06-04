package com.opschat.controller;

import com.opschat.dto.ApiResponse;
import com.opschat.dto.ChatRequest;
import com.opschat.service.SessionService;
import com.opschat.stream.SseResponseHandler;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.WorkflowOrchestrator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 聊天 Controller
 * 提供对话接口和会话管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final WorkflowOrchestrator workflowOrchestrator;
    private final SessionService sessionService;
    private final SseResponseHandler sseResponseHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(WorkflowOrchestrator workflowOrchestrator,
                          SessionService sessionService,
                          SseResponseHandler sseResponseHandler) {
        this.workflowOrchestrator = workflowOrchestrator;
        this.sessionService = sessionService;
        this.sseResponseHandler = sseResponseHandler;
    }

    /**
     * 流式对话接口
     * @param request 聊天请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = sseResponseHandler.createEmitter();

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            try {
                sseResponseHandler.sendError(emitter, "问题内容不能为空");
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try {
                log.info("收到流式对话请求 - SessionId: {}, Question: {}",
                        request.getId(), request.getQuestion());

                SessionService.SessionInfo session = sessionService.getOrCreateSession(request.getId());
                List<Map<String, String>> history = session.getMessageHistory();

                StringBuilder fullAnswer = new StringBuilder();
                int[] contentIndex = {0};

                Consumer<WorkflowEvent> wrappedConsumer = event -> {
                    try {
                        switch (event.getType()) {
                            case CONTENT:
                                sseResponseHandler.sendContent(emitter, event.getContent(), contentIndex[0]++);
                                fullAnswer.append(event.getContent());
                                break;
                            case TOOL_CALL:
                                sseResponseHandler.sendToolCall(emitter, event.getContent());
                                break;
                            case TOOL_RESULT:
                                sseResponseHandler.sendToolResult(emitter, event.getContent());
                                break;
                            case SEARCH_RESULT:
                                sseResponseHandler.sendSearchResult(emitter, event.getContent());
                                break;
                            case ERROR:
                                sseResponseHandler.sendError(emitter, event.getContent());
                                break;
                            case DONE:
                                sessionService.addMessage(session, request.getQuestion(), fullAnswer.toString());
                                sessionService.saveSession(session);
                                sseResponseHandler.sendDone(emitter);
                                break;
                            case TRACE:
                                log.trace("{}", event.getContent());
                                break;
                        }
                    } catch (IOException e) {
                        log.error("SSE 发送失败", e);
                    }
                };

                workflowOrchestrator.executeStream(request.getQuestion(), history, wrappedConsumer);

            } catch (Exception e) {
                log.error("流式对话执行失败", e);
                try {
                    sseResponseHandler.sendError(emitter, e.getMessage());
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 获取所有会话列表
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfoResponse>>> getSessions() {
        List<SessionService.SessionInfo> sessions = sessionService.getAllSessions();
        List<SessionInfoResponse> responses = new ArrayList<>();

        for (SessionService.SessionInfo session : sessions) {
            SessionInfoResponse response = new SessionInfoResponse();
            response.setSessionId(session.getSessionId());
            response.setTitle(session.getTitle());
            response.setMessagePairCount(session.getMessagePairCount());
            response.setCreateTime(session.getCreateTime());
            responses.add(response);
        }

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 删除指定会话
     * @param sessionId 会话ID
     * @return 响应
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取会话历史记录
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSessionHistory(@PathVariable String sessionId) {
        List<Map<String, String>> history = sessionService.getHistoryMessages(sessionId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * 清空会话历史记录
     * @param sessionId 会话ID
     * @return 响应
     */
    @DeleteMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<Void>> clearSessionMessages(@PathVariable String sessionId) {
        SessionService.SessionInfo session = sessionService.getSession(sessionId);
        if (session != null) {
            session.clearHistory();
            sessionService.saveSession(session);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 健康检查接口
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ==================== 内部响应类 ====================

    /**
     * 聊天结果响应
     */
    @Getter
    @Setter
    public static class ChatResult {
        private String answer;
        private boolean success;

        public static ChatResult success(String answer) {
            ChatResult result = new ChatResult();
            result.setAnswer(answer);
            result.setSuccess(true);
            return result;
        }

        public static ChatResult error(String message) {
            ChatResult result = new ChatResult();
            result.setAnswer(message);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 会话信息响应
     */
    @Getter
    @Setter
    public static class SessionInfoResponse {
        private String sessionId;
        private String title;
        private int messagePairCount;
        private long createTime;
    }
}