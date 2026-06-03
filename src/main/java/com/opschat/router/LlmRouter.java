package com.opschat.router;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.opschat.config.DashScopeProperties;
import com.opschat.workflow.WorkflowType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * LLM路由器
 * 使用LLM进行语义意图分类
 */
@Slf4j
@Component
public class LlmRouter implements Router {

    /**
     * 路由模型名称
     */
    @Value("${opschat.router.llm-model:qwen-plus}")
    private String model;

    /**
     * 是否启用LLM路由
     */
    @Value("${opschat.router.enabled:true}")
    private boolean enabled;

    /**
     * DashScope配置
     */
    private final DashScopeProperties dashScopeProperties;

    /**
     * 生成API客户端
     */
    private Generation generation;

    /**
     * JSON解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     * @param dashScopeProperties DashScope配置
     */
    public LlmRouter(DashScopeProperties dashScopeProperties) {
        this.dashScopeProperties = dashScopeProperties;
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        if (enabled) {
            Constants.apiKey = dashScopeProperties.getApiKey();
            generation = new Generation();
            log.info("[LlmRouter] 初始化完成，模型: {}", model);
        }
    }

    /**
     * 执行LLM路由
     * @param question 用户问题
     * @return 路由结果
     */
    @Override
    public RouteResult route(String question) {
        // 未启用或空输入处理
        if (!enabled || question == null || question.trim().isEmpty()) {
            log.debug("[LlmRouter] 未启用或空输入，返回默认CHAT");
            return RouteResult.of(WorkflowType.CHAT, 0.5f, "LlmRouter-Disabled");
        }

        try {
            // 构建分类提示词
            String prompt = buildClassificationPrompt(question);
            
            // 调用LLM
            String response = callLlm(prompt);
            
            // 解析响应
            LlmClassificationResult result = parseResponse(response);

            if (result != null) {
                log.info("[LlmRouter] 分类结果: type={}, confidence={}, needsClarify={}",
                        result.getType(), result.getConfidence(), result.isNeedsClarify());

                // 需要澄清或置信度不足
                if (result.isNeedsClarify() || result.getConfidence() < 0.75) {
                    return RouteResult.clarify(
                            result.getClarifyQuestion() != null ? result.getClarifyQuestion() 
                                    : "您的问题不够明确，请补充更多信息",
                            result.getMissingInfo() != null ? result.getMissingInfo() : Collections.emptyList(),
                            "LlmRouter");
                }

                // 返回分类结果
                WorkflowType type = WorkflowType.valueOf(result.getType().toUpperCase());
                return RouteResult.of(type, result.getConfidence(), "LlmRouter");
            }
        } catch (Exception e) {
            log.error("[LlmRouter] 分类失败", e);
        }

        // 降级到CHAT
        return RouteResult.of(WorkflowType.CHAT, 0.5f, "LlmRouter-Fallback");
    }

    /**
     * 构建分类提示词
     * @param question 用户问题
     * @return 提示词
     */
    private String buildClassificationPrompt(String question) {
        String prompt = "你是一个智能运维助手，需要对用户问题进行意图分类。\n\n" +
                "【分类体系】\n" +
                "- CHAT: 闲聊、问候、能力咨询、自我介绍、编程代码编写、技术概念解释等直接回答问题\n" +
                "- TOOL: 单步系统查询（日志查询、监控指标、告警信息、订单查询、trace追踪等）\n" +
                "- RAG: 技术知识库问答（SOP文档、架构文档、排障经验、配置说明、运维手册）\n" +
                "- REACT: 多步骤推理分析（根因定位、故障分析、性能优化建议）\n" +
                "- CLARIFY: 信息不足，需要用户补充关键信息\n\n" +
                "【用户问题】\n" +
                question + "\n\n" +
                "【输出要求】\n" +
                "请以JSON格式输出分类结果，不要有其他内容：\n" +
                "{\n" +
                "    \"type\": \"分类类型\",\n" +
                "    \"confidence\": 0.0-1.0,\n" +
                "    \"needsClarify\": true或false,\n" +
                "    \"clarifyQuestion\": \"如果需要澄清，填写追问问题\",\n" +
                "    \"missingInfo\": [\"缺失的信息列表\"]\n" +
                "}\n";
        return prompt;
    }

    /**
     * 调用LLM
     * @param prompt 提示词
     * @return 响应内容
     */
    private String callLlm(String prompt) throws Exception {
        Message message = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(dashScopeProperties.getApiKey())
                .model(model)
                .messages(Collections.singletonList(message))
                .resultFormat("message")
                .build();

        GenerationResult result = generation.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    }

    /**
     * 解析LLM响应
     * @param response 响应内容
     * @return 分类结果
     */
    private LlmClassificationResult parseResponse(String response) {
        try {
            return objectMapper.readValue(response, LlmClassificationResult.class);
        } catch (Exception e) {
            log.warn("[LlmRouter] 解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM分类结果内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmClassificationResult {
        private String type;
        private float confidence;
        private boolean needsClarify;
        private String clarifyQuestion;
        private List<String> missingInfo;
    }
}