package com.opschat.service;

import com.alibaba.dashscope.common.Message;
import com.opschat.llm.LlmService;
import com.opschat.llm.PromptTemplateService;
import com.opschat.stream.WorkflowEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final LlmService llmService;
    private final PromptTemplateService promptTemplate;
    private final VectorSearchService vectorSearchService;

    @Autowired
    public RagService(LlmService llmService,
                      PromptTemplateService promptTemplate,
                      VectorSearchService vectorSearchService) {
        this.llmService = llmService;
        this.promptTemplate = promptTemplate;
        this.vectorSearchService = vectorSearchService;
    }

    public String query(String question) {
        log.info("[RagService] 执行知识库检索: {}", question);
        List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilarDocuments(question, 5);

        if (results == null || results.isEmpty()) {
            log.info("[RagService] 未找到相关文档，使用通用对话");
            List<Message> messages = promptTemplate.buildChatPrompt(null, question);
            return llmService.chat(messages);
        }

        String context = results.stream()
                .map(r -> r.getContent())
                .collect(Collectors.joining("\n\n"));

        log.info("[RagService] 检索到 {} 个相关文档块", results.size());
        return llmService.chat(promptTemplate.buildRagPrompt(question, context));
    }

    public void queryStream(String question, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[RagService] 执行流式知识库检索: {}", question);

        eventConsumer.accept(WorkflowEvent.trace("开始检索知识库..."));

        List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilarDocuments(question, 5);

        if (results == null || results.isEmpty()) {
            eventConsumer.accept(WorkflowEvent.searchResult("未找到相关文档"));
            eventConsumer.accept(WorkflowEvent.content("抱歉，知识库中没有找到与您问题相关的内容。"));
            eventConsumer.accept(WorkflowEvent.done());
            return;
        }

        String context = results.stream()
                .map(r -> r.getContent())
                .collect(Collectors.joining("\n\n"));

        eventConsumer.accept(WorkflowEvent.searchResult("检索到 " + results.size() + " 个相关文档"));

        var messages = promptTemplate.buildRagPrompt(question, context);
        llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
        eventConsumer.accept(WorkflowEvent.done());
    }
}