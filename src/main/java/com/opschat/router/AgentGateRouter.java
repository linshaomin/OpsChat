package com.opschat.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Agent Gate Router
 * 轻量级门限路由器，仅判断是否进入 React Agent
 * 
 * 核心职责：
 * - 判断简单问题（直接回答） vs 复杂问题（需要 Agent）
 * - 不做意图分类，不做业务路由
 * - 输出：useAgent + confidence
 */
@Slf4j
@Component
public class AgentGateRouter {

    /**
     * 是否启用 Agent 路由
     */
    @Value("${opschat.router.agent-enabled:true}")
    private boolean agentEnabled;

    /**
     * 简单问题阈值：低于此值进入 Agent
     */
    @Value("${opschat.router.agent-threshold:0.9}")
    private float agentThreshold;

    /**
     * 问候语集合
     */
    private static final HashSet<String> GREETINGS = new HashSet<>();

    /**
     * 告别语集合
     */
    private static final HashSet<String> FAREWELLS = new HashSet<>();

    /**
     * 能力咨询关键词
     */
    private static final HashSet<String> CAPABILITY_QUERIES = new HashSet<>();

    /**
     * 简单问题关键词（不需要工具的问题）
     */
    private static final HashSet<String> SIMPLE_QUESTIONS = new HashSet<>();

    /**
     * 复杂问题关键词（需要工具的问题）
     */
    private static final HashSet<String> COMPLEX_QUESTIONS = new HashSet<>();

    /**
     * 系统命令正则（以/开头）
     */
    private static final Pattern SYSTEM_COMMAND_PATTERN = Pattern.compile("^/\\w+$", Pattern.CASE_INSENSITIVE);

    /**
     * 结构化ID正则（订单号、TraceID等）
     */
    private static final Pattern ID_PATTERN = Pattern.compile("(order|trace|ticket|log|task|job)[:：]?\\s*(\\d{6,})", Pattern.CASE_INSENSITIVE);

    static {
        // 问候语
        GREETINGS.add("你好");
        GREETINGS.add("您好");
        GREETINGS.add("嗨");
        GREETINGS.add("hi");
        GREETINGS.add("hello");
        GREETINGS.add("哈喽");
        GREETINGS.add("早上好");
        GREETINGS.add("下午好");
        GREETINGS.add("晚上好");

        // 告别语
        FAREWELLS.add("再见");
        FAREWELLS.add("拜拜");
        FAREWELLS.add("谢谢");
        FAREWELLS.add("感谢");
        FAREWELLS.add("bye");
        FAREWELLS.add("goodbye");
        FAREWELLS.add("结束");

        // 能力咨询
        CAPABILITY_QUERIES.add("你能做什么");
        CAPABILITY_QUERIES.add("你是谁");
        CAPABILITY_QUERIES.add("介绍一下");
        CAPABILITY_QUERIES.add("功能");
        CAPABILITY_QUERIES.add("帮助");
        CAPABILITY_QUERIES.add("能力");
        CAPABILITY_QUERIES.add("可以");

        // 简单问题关键词（直接回答即可）
        SIMPLE_QUESTIONS.add("什么是");
        SIMPLE_QUESTIONS.add("解释");
        SIMPLE_QUESTIONS.add("说明");
        SIMPLE_QUESTIONS.add("定义");
        SIMPLE_QUESTIONS.add("概念");
        SIMPLE_QUESTIONS.add("原理");
        SIMPLE_QUESTIONS.add("怎么做");
        SIMPLE_QUESTIONS.add("如何");
        SIMPLE_QUESTIONS.add("写一个");
        SIMPLE_QUESTIONS.add("编写");
        SIMPLE_QUESTIONS.add("代码");

        // 复杂问题关键词（需要工具）
        COMPLEX_QUESTIONS.add("查询");
        COMPLEX_QUESTIONS.add("查看");
        COMPLEX_QUESTIONS.add("获取");
        COMPLEX_QUESTIONS.add("日志");
        COMPLEX_QUESTIONS.add("监控");
        COMPLEX_QUESTIONS.add("告警");
        COMPLEX_QUESTIONS.add("订单");
        COMPLEX_QUESTIONS.add("trace");
        COMPLEX_QUESTIONS.add("Trace");
        COMPLEX_QUESTIONS.add("工单");
        COMPLEX_QUESTIONS.add("任务");
        COMPLEX_QUESTIONS.add("分析");
        COMPLEX_QUESTIONS.add("定位");
        COMPLEX_QUESTIONS.add("排查");
        COMPLEX_QUESTIONS.add("故障");
        COMPLEX_QUESTIONS.add("异常");
        COMPLEX_QUESTIONS.add("性能");
        COMPLEX_QUESTIONS.add("延迟");
        COMPLEX_QUESTIONS.add("错误");
        COMPLEX_QUESTIONS.add("问题");
        COMPLEX_QUESTIONS.add("原因");
        COMPLEX_QUESTIONS.add("根因");
        COMPLEX_QUESTIONS.add("文档");
        COMPLEX_QUESTIONS.add("SOP");
        COMPLEX_QUESTIONS.add("手册");
    }

    /**
     * 判断是否需要进入 Agent
     * @param question 用户问题
     * @return GateResult 门限结果
     */
    public GateResult shouldUseAgent(String question) {
        if (!agentEnabled) {
            log.debug("[AgentGateRouter] Agent 未启用，直接走 Chat 模式");
            return GateResult.builder()
                    .useAgent(false)
                    .confidence(0f)
                    .reason("Agent disabled")
                    .build();
        }

        if (question == null || question.trim().isEmpty()) {
            log.debug("[AgentGateRouter] 空输入");
            return GateResult.builder()
                    .useAgent(false)
                    .confidence(0f)
                    .reason("Empty input")
                    .build();
        }

        String trimmed = question.trim();

        // 短输入检测（小于2个字符）
        if (trimmed.length() < 2) {
            log.debug("[AgentGateRouter] 输入过短");
            return GateResult.builder()
                    .useAgent(false)
                    .confidence(0f)
                    .reason("Input too short")
                    .build();
        }

        // 问候语检测 - 直接回答
        if (GREETINGS.contains(trimmed.toLowerCase())) {
            log.debug("[AgentGateRouter] 检测到问候语");
            return GateResult.builder()
                    .useAgent(false)
                    .confidence(0.99f)
                    .reason("Greeting")
                    .build();
        }

        // 告别语检测 - 直接回答
        if (FAREWELLS.contains(trimmed.toLowerCase())) {
            log.debug("[AgentGateRouter] 检测到告别语");
            return GateResult.builder()
                    .useAgent(false)
                    .confidence(0.99f)
                    .reason("Farewell")
                    .build();
        }

        // 能力咨询检测 - 直接回答
        for (String query : CAPABILITY_QUERIES) {
            if (trimmed.contains(query)) {
                log.debug("[AgentGateRouter] 检测到能力咨询");
                return GateResult.builder()
                        .useAgent(false)
                        .confidence(0.95f)
                        .reason("Capability query")
                        .build();
            }
        }

        // 系统命令检测 - 需要 Agent
        if (SYSTEM_COMMAND_PATTERN.matcher(trimmed).matches()) {
            log.debug("[AgentGateRouter] 检测到系统命令");
            return GateResult.builder()
                    .useAgent(true)
                    .confidence(0.99f)
                    .reason("System command")
                    .build();
        }

        // 结构化ID检测 - 需要 Agent
        if (ID_PATTERN.matcher(trimmed).find()) {
            log.debug("[AgentGateRouter] 检测到结构化ID");
            return GateResult.builder()
                    .useAgent(true)
                    .confidence(0.95f)
                    .reason("Structured ID")
                    .build();
        }

        // 复杂问题关键词检测 - 需要 Agent
        for (String query : COMPLEX_QUESTIONS) {
            if (trimmed.contains(query)) {
                log.debug("[AgentGateRouter] 检测到复杂问题关键词: {}", query);
                return GateResult.builder()
                        .useAgent(true)
                        .confidence(0.85f)
                        .reason("Complex question keyword: " + query)
                        .build();
            }
        }

        // 简单问题关键词检测 - 直接回答
        for (String query : SIMPLE_QUESTIONS) {
            if (trimmed.contains(query)) {
                log.debug("[AgentGateRouter] 检测到简单问题关键词: {}", query);
                return GateResult.builder()
                        .useAgent(false)
                        .confidence(0.8f)
                        .reason("Simple question keyword: " + query)
                        .build();
            }
        }

        // 默认：进入 Agent（让 Agent 决定如何处理）
        log.debug("[AgentGateRouter] 默认进入 Agent");
        return GateResult.builder()
                .useAgent(true)
                .confidence(agentThreshold)
                .reason("Default to Agent")
                .build();
    }

    /**
     * 门限结果
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class GateResult {
        /**
         * 是否使用 Agent
         */
        private boolean useAgent;

        /**
         * 置信度
         */
        private float confidence;

        /**
         * 决策原因
         */
        private String reason;
    }
}