package com.opschat.llm;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.utils.Constants;
import com.opschat.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM服务类
 * 封装阿里云通义千问API调用
 */
@Slf4j
@Service
public class LlmService {

    /**
     * DashScope配置
     */
    private final DashScopeProperties dashScopeProperties;

    /**
     * 生成API客户端
     */
    private Generation generation;

    /**
     * 构造函数
     * @param dashScopeProperties DashScope配置
     */
    public LlmService(DashScopeProperties dashScopeProperties) {
        this.dashScopeProperties = dashScopeProperties;
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        Constants.apiKey = dashScopeProperties.getApiKey();
        generation = new Generation();
        log.info("[LlmService] 初始化完成");
    }

    /**
     * 同步调用LLM
     * @param messages 消息列表
     * @return 响应内容
     */
    public String chat(List<Message> messages) {
        return chat(messages, dashScopeProperties.getChat().getModel());
    }

    /**
     * 同步调用LLM（指定模型）
     * @param messages 消息列表
     * @param model 模型名称
     * @return 响应内容
     */
    public String chat(List<Message> messages, String model) {
        try {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(dashScopeProperties.getApiKey())
                    .model(model)
                    .incrementalOutput(false)
                    .resultFormat("message")
                    .messages(messages)
                    .build();

            GenerationResult result = generation.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("[LlmService] 调用失败", e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用LLM
     * @param messages 消息列表
     * @param onChunk 回调函数，处理每个内容块
     */
    public void streamChat(List<Message> messages, Consumer<String> onChunk) {
        streamChat(messages, dashScopeProperties.getChat().getModel(), onChunk);
    }

    /**
     * 流式调用LLM（指定模型）
     * @param messages 消息列表
     * @param model 模型名称
     * @param onChunk 回调函数，处理每个内容块
     */
    public void streamChat(List<Message> messages, String model, Consumer<String> onChunk) {
        try {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(dashScopeProperties.getApiKey())
                    .model(model)
                    .incrementalOutput(true)
                    .resultFormat("message")
                    .messages(messages)
                    .build();

            generation.streamCall(param).blockingForEach(result -> {
                if (result.getOutput() != null && result.getOutput().getChoices() != null) {
                    String content = result.getOutput().getChoices().get(0).getMessage().getContent();
                    if (content != null && !content.isEmpty()) {
                        onChunk.accept(content);
                    }
                }
            });
        } catch (Exception e) {
            log.error("[LlmService] 流式调用失败", e);
            throw new RuntimeException("LLM流式调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取默认模型名称
     * @return 模型名称
     */
    public String getDefaultModel() {
        return dashScopeProperties.getChat().getModel();
    }
}