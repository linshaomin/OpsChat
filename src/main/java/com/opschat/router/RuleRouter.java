package com.opschat.router;

import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则路由器
 * 处理高确定性场景，减少LLM调用
 */
@Slf4j
@Component
public class RuleRouter implements Router {

    /**
     * 高置信度阈值
     */
    private static final float HIGH_CONFIDENCE = 0.99f;

    /**
     * 系统命令正则（以/开头）
     */
    private static final Pattern SYSTEM_COMMAND_PATTERN = Pattern.compile("^/\\w+$", Pattern.CASE_INSENSITIVE);

    /**
     * 结构化ID正则（订单号、TraceID等）
     */
    private static final Pattern ID_PATTERN = Pattern.compile("(order|trace|ticket|log|task|job)[:：]?\\s*(\\d{6,})", Pattern.CASE_INSENSITIVE);

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
     * 工具查询关键词
     */
    private static final HashSet<String> TOOL_QUERIES = new HashSet<>();

    static {
        // 初始化问候语
        GREETINGS.add("你好");
        GREETINGS.add("您好");
        GREETINGS.add("嗨");
        GREETINGS.add("hi");
        GREETINGS.add("hello");
        GREETINGS.add("哈喽");
        GREETINGS.add("早上好");
        GREETINGS.add("下午好");
        GREETINGS.add("晚上好");
        GREETINGS.add("Hi");
        GREETINGS.add("Hello");

        // 初始化告别语
        FAREWELLS.add("再见");
        FAREWELLS.add("拜拜");
        FAREWELLS.add("谢谢");
        FAREWELLS.add("感谢");
        FAREWELLS.add("bye");
        FAREWELLS.add("goodbye");
        FAREWELLS.add("结束");

        // 初始化能力咨询
        CAPABILITY_QUERIES.add("你能做什么");
        CAPABILITY_QUERIES.add("你是谁");
        CAPABILITY_QUERIES.add("介绍一下");
        CAPABILITY_QUERIES.add("功能");
        CAPABILITY_QUERIES.add("帮助");
        CAPABILITY_QUERIES.add("能力");

        // 初始化工具查询关键词
        TOOL_QUERIES.add("查询");
        TOOL_QUERIES.add("查看");
        TOOL_QUERIES.add("获取");
        TOOL_QUERIES.add("日志");
        TOOL_QUERIES.add("监控");
        TOOL_QUERIES.add("告警");
        TOOL_QUERIES.add("订单");
        TOOL_QUERIES.add("trace");
        TOOL_QUERIES.add("Trace");
        TOOL_QUERIES.add("工单");
        TOOL_QUERIES.add("任务");
    }

    /**
     * 执行规则路由
     * @param question 用户问题
     * @return 路由结果
     */
    @Override
    public RouteResult route(String question) {
        // 空输入检查
        if (question == null || question.trim().isEmpty()) {
            log.debug("[RuleRouter] 空输入");
            return RouteResult.clarify("请告诉我您想了解什么？", List.of("具体问题"), "RuleRouter-Empty");
        }

        String trimmed = question.trim();

        // 问候语检测
        if (GREETINGS.contains(trimmed.toLowerCase())) {
            log.debug("[RuleRouter] 检测到问候语: {}", trimmed);
            return RouteResult.of(WorkflowType.CHAT, HIGH_CONFIDENCE, "RuleRouter-Greeting");
        }

        // 告别语检测
        if (FAREWELLS.contains(trimmed.toLowerCase())) {
            log.debug("[RuleRouter] 检测到告别语: {}", trimmed);
            return RouteResult.of(WorkflowType.CHAT, HIGH_CONFIDENCE, "RuleRouter-Farewell");
        }

        // 短输入检测（小于2个字符）
        if (trimmed.length() < 2) {
            log.debug("[RuleRouter] 输入过短: {}", trimmed);
            return RouteResult.clarify("您的输入太短了，请提供更完整的问题", List.of("完整问题"), "RuleRouter-Short");
        }

        // 系统命令检测
        if (SYSTEM_COMMAND_PATTERN.matcher(trimmed).matches()) {
            log.debug("[RuleRouter] 检测到系统命令: {}", trimmed);
            return RouteResult.of(WorkflowType.TOOL, HIGH_CONFIDENCE, "RuleRouter-Command");
        }

        // 能力咨询检测
        for (String query : CAPABILITY_QUERIES) {
            if (trimmed.contains(query)) {
                log.debug("[RuleRouter] 检测到能力咨询: {}", trimmed);
                return RouteResult.of(WorkflowType.CHAT, HIGH_CONFIDENCE, "RuleRouter-Capability");
            }
        }

        // 结构化ID检测
        Matcher idMatcher = ID_PATTERN.matcher(trimmed);
        if (idMatcher.find()) {
            String idType = idMatcher.group(1);
            String idValue = idMatcher.group(2);
            log.debug("[RuleRouter] 检测到结构化ID: {} = {}", idType, idValue);
            return RouteResult.of(WorkflowType.TOOL, HIGH_CONFIDENCE, "RuleRouter-StructuredId",
                    "检测到结构化ID: " + idType + " = " + idValue);
        }

        // 工具查询检测
        for (String query : TOOL_QUERIES) {
            if (trimmed.contains(query)) {
                log.debug("[RuleRouter] 检测到工具查询关键词: {}", query);
                return RouteResult.of(WorkflowType.TOOL, 0.85f, "RuleRouter-ToolKeyword");
            }
        }

        // 未匹配到规则
        return RouteResult.of(null, 0f, "RuleRouter-NoMatch");
    }
}