package com.opschat.controller;

import com.opschat.service.SessionService;
import com.opschat.stream.SseResponseHandler;
import com.opschat.stream.WorkflowEvent;
import com.opschat.stream.WorkflowEventPublisher;
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

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final WorkflowOrchestrator workflowOrchestrator;
    private final SessionService sessionService;
    private final SseResponseHandler sseResponseHandler;
    private final WorkflowEventPublisher eventPublisher;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(WorkflowOrchestrator workflowOrchestrator,
                          SessionService sessionService,
                          SseResponseHandler sseResponseHandler,
                          WorkflowEventPublisher eventPublisher) {
        this.workflowOrchestrator = workflowOrchestrator;
        this.sessionService = sessionService;
        this.sseResponseHandler = sseResponseHandler;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            log.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            SessionService.SessionInfo session = sessionService.getOrCreateSession(request.getId());
            List<Map<String, String>> history = session.getMessageHistory();

            String answer = workflowOrchestrator.execute(request.getQuestion(), history);

            sessionService.addMessage(session, request.getQuestion(), answer);
            sessionService.saveSession(session);

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));

        } catch (Exception e) {
            log.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

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

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSessionHistory(@PathVariable String sessionId) {
        List<Map<String, String>> history = sessionService.getHistoryMessages(sessionId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @Getter
    @Setter
    public static class ChatRequest {
        private String id;
        private String question;
    }

    @Getter
    @Setter
    public static class ChatResponse {
        private String answer;
        private boolean success;

        public static ChatResponse success(String answer) {
            ChatResponse response = new ChatResponse();
            response.setAnswer(answer);
            response.setSuccess(true);
            return response;
        }

        public static ChatResponse error(String message) {
            ChatResponse response = new ChatResponse();
            response.setAnswer(message);
            response.setSuccess(false);
            return response;
        }
    }

    @Getter
    @Setter
    public static class SessionInfoResponse {
        private String sessionId;
        private String title;
        private int messagePairCount;
        private long createTime;
    }

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }
    }
}
