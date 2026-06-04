package com.opschat.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.opschat.agent.tool.CurrentTimeTool;
import com.opschat.agent.tool.KnowledgeBaseTool;
import com.opschat.agent.tool.MonitorMetricTool;
import com.opschat.agent.tool.QCloudLogTool;
import com.opschat.llm.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ReactAgent 工厂类
 * 统一创建 ReactAgent 实例，使用阿里云官方 ReactAgent
 * 
 * 架构：
 * ReactAgentFactory → ReactAgent（官方） → LLM自动循环 → Final Answer
 */
@Slf4j
@Component
public class ReactAgentFactory {

    @Value("${opschat.ai.dashscope.api-key}")
    private String apiKey;

    private final PromptTemplateService promptTemplateService;
    private final QCloudLogTool qCloudLogTool;
    private final MonitorMetricTool monitorMetricTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final CurrentTimeTool currentTimeTool;

    public ReactAgentFactory(PromptTemplateService promptTemplateService,
                             QCloudLogTool qCloudLogTool,
                             MonitorMetricTool monitorMetricTool,
                             KnowledgeBaseTool knowledgeBaseTool,
                             CurrentTimeTool currentTimeTool) {
        this.promptTemplateService = promptTemplateService;
        this.qCloudLogTool = qCloudLogTool;
        this.monitorMetricTool = monitorMetricTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.currentTimeTool = currentTimeTool;
    }

    /**
     * 创建 ReactAgent 实例
     *
     * @param history 对话历史
     * @return ReactAgent 实例
     */
    public ReactAgent createReactAgent(List<Map<String, String>> history) {
        log.info("[ReactAgentFactory] 创建 ReactAgent");

        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();

        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(0.7)
                        .withMaxToken(2000)
                        .build())
                .build();

        String systemPrompt = promptTemplateService.buildReactAgentPrompt(history);
        Object[] methodTools = buildMethodTools();

        log.info("[ReactAgentFactory] 可用工具数量: {}", methodTools.length);

        return ReactAgent.builder()
                .name("ai_ops_agent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(methodTools)
                .build();
    }

    /**
     * 构建方法工具数组
     */
    private Object[] buildMethodTools() {
        return new Object[]{
                currentTimeTool,
                knowledgeBaseTool,
                monitorMetricTool,
                qCloudLogTool
        };
    }
}