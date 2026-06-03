package com.opschat.workflow.impl;

import com.alibaba.dashscope.common.Message;
import com.opschat.llm.LlmService;
import com.opschat.llm.PromptTemplateService;
import com.opschat.stream.WorkflowEvent;
import com.opschat.workflow.WorkflowStrategy;
import com.opschat.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具工作流
 * 单步系统查询，支持腾讯云日志查询和当前时间查询
 */
@Slf4j
@Service
public class ToolWorkflow implements WorkflowStrategy {

    /**
     * LLM服务
     */
    private final LlmService llmService;

    /**
     * 提示词模板服务
     */
    private final PromptTemplateService promptTemplate;

    /**
     * 日志查询正则模式
     */
    private static final Pattern LOG_QUERY_PATTERN = Pattern.compile(
            "(日志|log|Log|LOG).*?(查询|查看|获取|搜索|search|find)", Pattern.CASE_INSENSITIVE);

    /**
     * 时间查询正则模式
     */
    private static final Pattern TIME_QUERY_PATTERN = Pattern.compile(
            "(时间|现在|当前|几点|日期|date|time|now)", Pattern.CASE_INSENSITIVE);

    /**
     * 订单查询正则模式
     */
    private static final Pattern ORDER_QUERY_PATTERN = Pattern.compile(
            "订单|order|ORDER|订单号|orderNo|order_no", Pattern.CASE_INSENSITIVE);

    /**
     * 构造函数
     * @param llmService LLM服务
     * @param promptTemplate 提示词模板服务
     */
    public ToolWorkflow(LlmService llmService, PromptTemplateService promptTemplate) {
        this.llmService = llmService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 获取工作流类型
     * @return TOOL类型
     */
    @Override
    public WorkflowType getType() {
        return WorkflowType.TOOL;
    }

    /**
     * 同步执行工具工作流
     * @param question 用户问题
     * @param history 历史对话记录
     * @return 回答内容
     */
    @Override
    public String execute(String question, List<Map<String, String>> history) {
        log.info("[ToolWorkflow] 执行工具工作流");
        StringBuilder fullContent = new StringBuilder();
        executeStream(question, history, event -> {
            if (event.getType() == com.opschat.stream.WorkflowEventType.CONTENT) {
                fullContent.append(event.getContent());
            }
        });
        return fullContent.toString();
    }

    /**
     * 流式执行工具工作流
     * 支持：腾讯云日志查询、当前时间查询、订单查询等
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    @Override
    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        log.info("[ToolWorkflow] 执行流式工具工作流: {}", question);
        try {
            // 识别工具类型并执行
            String toolType = identifyToolType(question);
            
            switch (toolType) {
                case "qcloud_log":
                    executeQCloudLogQuery(question, eventConsumer);
                    break;
                case "current_time":
                    executeCurrentTimeQuery(eventConsumer);
                    break;
                case "order":
                    executeOrderQuery(question, eventConsumer);
                    break;
                default:
                    executeGenericQuery(question, history, eventConsumer);
            }

            // 发送完成事件
            eventConsumer.accept(WorkflowEvent.done());
            
        } catch (Exception e) {
            log.error("[ToolWorkflow] 执行失败", e);
            eventConsumer.accept(WorkflowEvent.error(e.getMessage()));
        }
    }

    /**
     * 识别工具类型
     * @param question 用户问题
     * @return 工具类型标识
     */
    private String identifyToolType(String question) {
        if (LOG_QUERY_PATTERN.matcher(question).find()) {
            return "qcloud_log";
        }
        if (TIME_QUERY_PATTERN.matcher(question).find()) {
            return "current_time";
        }
        if (ORDER_QUERY_PATTERN.matcher(question).find()) {
            return "order";
        }
        return "generic";
    }

    /**
     * 执行腾讯云日志查询
     * @param question 用户问题
     * @param eventConsumer 事件消费者
     */
    private void executeQCloudLogQuery(String question, Consumer<WorkflowEvent> eventConsumer) {
        eventConsumer.accept(WorkflowEvent.toolCall("QCloud Log Service", "查询腾讯云日志"));
        
        // 模拟腾讯云日志查询结果
        // 实际实现中需要调用腾讯云CLS SDK
        String logResult = simulateQCloudLogQuery(question);
        
        eventConsumer.accept(WorkflowEvent.toolResult("QCloud Log Service", logResult));
        
        // 使用LLM总结日志结果
        List<Message> messages = promptTemplate.buildToolPrompt(null, question, logResult);
        llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
    }

    /**
     * 模拟腾讯云日志查询
     * @param question 用户问题
     * @return 日志查询结果
     */
    private String simulateQCloudLogQuery(String question) {
        // 提取时间范围（简单实现）
        String timeRange = extractTimeRange(question);
        
        StringBuilder result = new StringBuilder();
        result.append("【腾讯云日志查询结果】\n");
        result.append("查询时间范围: ").append(timeRange).append("\n");
        result.append("日志主题: ops-prod-api\n");
        result.append("查询条数: 100 条\n\n");
        result.append("【最近5条日志】\n");
        result.append("[2024-01-15 14:30:01] INFO  [GET /api/v1/orders] status=200 latency=12ms\n");
        result.append("[2024-01-15 14:30:02] WARN  [POST /api/v1/pay] status=400 error=invalid_param\n");
        result.append("[2024-01-15 14:30:03] INFO  [GET /api/v1/users] status=200 latency=8ms\n");
        result.append("[2024-01-15 14:30:04] ERROR [GET /api/v1/products] status=500 error=db_timeout\n");
        result.append("[2024-01-15 14:30:05] INFO  [POST /api/v1/orders] status=201 latency=45ms\n");
        
        return result.toString();
    }

    /**
     * 提取时间范围（简单实现）
     * @param question 用户问题
     * @return 时间范围描述
     */
    private String extractTimeRange(String question) {
        if (question.contains("今天") || question.contains("今日")) {
            return "今天";
        } else if (question.contains("昨天")) {
            return "昨天";
        } else if (question.contains("最近")) {
            return "最近1小时";
        } else if (question.contains("上周")) {
            return "上周";
        }
        return "最近1小时";
    }

    /**
     * 执行当前时间查询
     * @param eventConsumer 事件消费者
     */
    private void executeCurrentTimeQuery(Consumer<WorkflowEvent> eventConsumer) {
        eventConsumer.accept(WorkflowEvent.toolCall("Time Service", "获取当前时间"));
        
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
        String timeResult = "当前时间: " + now.format(formatter);
        
        eventConsumer.accept(WorkflowEvent.toolResult("Time Service", timeResult));
        
        // 直接返回时间结果
        eventConsumer.accept(WorkflowEvent.content("当前时间是：" + now.format(formatter)));
    }

    /**
     * 执行订单查询
     * @param question 用户问题
     * @param eventConsumer 事件消费者
     */
    private void executeOrderQuery(String question, Consumer<WorkflowEvent> eventConsumer) {
        eventConsumer.accept(WorkflowEvent.toolCall("Order Service", "查询订单信息"));
        
        // 提取订单号
        String orderId = extractOrderId(question);
        
        // 模拟订单查询结果
        String orderResult = simulateOrderQuery(orderId);
        
        eventConsumer.accept(WorkflowEvent.toolResult("Order Service", orderResult));
        
        // 使用LLM总结订单信息
        List<Message> messages = promptTemplate.buildToolPrompt(null, question, orderResult);
        llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
    }

    /**
     * 提取订单号
     * @param question 用户问题
     * @return 订单号
     */
    private String extractOrderId(String question) {
        Pattern pattern = Pattern.compile("(\\d{6,})");
        Matcher matcher = pattern.matcher(question);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未知订单号";
    }

    /**
     * 模拟订单查询
     * @param orderId 订单号
     * @return 订单信息
     */
    private String simulateOrderQuery(String orderId) {
        StringBuilder result = new StringBuilder();
        result.append("【订单查询结果】\n");
        result.append("订单号: ").append(orderId).append("\n");
        result.append("订单状态: 已完成\n");
        result.append("下单时间: 2024-01-15 10:30:00\n");
        result.append("支付时间: 2024-01-15 10:32:15\n");
        result.append("商品名称: 智能运维助手服务\n");
        result.append("商品数量: 1\n");
        result.append("订单金额: 999.00 元\n");
        result.append("支付方式: 微信支付\n");
        result.append("收货人: 张三\n");
        result.append("联系电话: 138****8888\n");
        
        return result.toString();
    }

    /**
     * 执行通用工具查询
     * @param question 用户问题
     * @param history 历史对话记录
     * @param eventConsumer 事件消费者
     */
    private void executeGenericQuery(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        eventConsumer.accept(WorkflowEvent.toolCall("query", "执行系统查询"));
        
        // 模拟通用查询结果
        String toolResult = "[系统查询结果] 这里显示与 '" + question + "' 相关的查询数据。\n\n" +
                "在实际部署中，这里会连接相应的业务系统进行数据查询。";
        
        eventConsumer.accept(WorkflowEvent.toolResult("query", toolResult));
        
        // 使用LLM总结结果
        List<Message> messages = promptTemplate.buildToolPrompt(history, question, toolResult);
        llmService.streamChat(messages, chunk -> eventConsumer.accept(WorkflowEvent.content(chunk)));
    }
}