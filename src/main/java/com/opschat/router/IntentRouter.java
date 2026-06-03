package com.opschat.router;

import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 意图路由器
 * 编排RuleRouter和LlmRouter实现二级路由
 */
@Slf4j
@Component
public class IntentRouter {

    /**
     * 规则路由器
     */
    private final RuleRouter ruleRouter;

    /**
     * LLM路由器
     */
    private final LlmRouter llmRouter;

    /**
     * 规则匹配阈值
     */
    private static final float RULE_THRESHOLD = 0.95f;

    /**
     * 构造函数
     * @param ruleRouter 规则路由器
     * @param llmRouter LLM路由器
     */
    public IntentRouter(RuleRouter ruleRouter, LlmRouter llmRouter) {
        this.ruleRouter = ruleRouter;
        this.llmRouter = llmRouter;
    }

    /**
     * 执行路由
     * @param question 用户问题
     * @return 路由结果
     */
    public RouteResult route(String question) {
        return route(question, null);
    }

    /**
     * 执行路由（带历史记录）
     * @param question 用户问题
     * @param history 历史对话记录
     * @return 路由结果
     */
    public RouteResult route(String question, List<Map<String, String>> history) {
        // 空输入检查
        if (question == null || question.trim().isEmpty()) {
            log.debug("[IntentRouter] 空输入");
            return RouteResult.clarify("请告诉我您想了解什么？", List.of("具体问题"), "IntentRouter-Empty");
        }

        log.debug("[IntentRouter] 开始路由: {}", question);

        // 第一级：规则路由
        RouteResult ruleResult = ruleRouter.route(question);

        // 规则命中（高置信度）
        if (ruleResult.isValid(RULE_THRESHOLD)) {
            log.info("[IntentRouter] 规则命中: {} -> {}", question, ruleResult.getWorkflowType());
            return ruleResult;
        }

        // 规则要求澄清
        if (ruleResult.requiresClarification()) {
            log.info("[IntentRouter] 规则要求澄清: {}", question);
            return ruleResult;
        }

        // 第二级：LLM路由
        RouteResult llmResult = llmRouter.route(question);

        if (llmResult.getWorkflowType() != null) {
            log.info("[IntentRouter] LLM分类: {} -> {} (confidence={})", 
                    question, llmResult.getWorkflowType(), llmResult.getConfidence());
            return llmResult;
        }

        // 降级到CHAT
        log.info("[IntentRouter] 降级到CHAT: {}", question);
        return RouteResult.of(WorkflowType.CHAT, 0.5f, "IntentRouter-Fallback");
    }
}