package com.opschat.llm;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt模板服务
 * 提供各种场景的提示词模板构建
 */
@Service
public class PromptTemplateService {

    /**
     * 默认聊天模型
     */
    private static final String MODEL_CHAT = "qwen-turbo";

    /**
     * 默认推理模型
     */
    private static final String MODEL_REACT = "qwen-plus";

    /**
     * 构建聊天提示词
     * @param history 历史对话记录
     * @param question 当前问题
     * @return 消息列表
     */
    public List<Message> buildChatPrompt(List<Map<String, String>> history, String question) {
        List<Message> messages = new ArrayList<>();

        // 添加系统提示词
        messages.add(Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(buildChatSystemPrompt())
                .build());

        // 添加历史消息
        messages.addAll(buildHistoryMessages(history));

        // 添加当前问题
        messages.add(Message.builder()
                .role(Role.USER.getValue())
                .content(question)
                .build());

        return messages;
    }

    /**
     * 构建聊天系统提示词
     * @return 系统提示词
     */
    private String buildChatSystemPrompt() {
        return "【角色】你是一个专业的智能运维助手（AI Ops Assistant）。\n\n" +
                "【职责范围】\n" +
                "1. 回答与运维相关的问题：监控告警、日志分析、系统排查、性能优化等\n" +
                "2. 解答技术问题：编程语言、框架使用、工具操作等\n" +
                "3. 提供操作指南：标准操作流程(SOP)、故障排查步骤等\n\n" +
                "【回答原则】\n" +
                "1. 只回答与运维/技术相关的问题\n" +
                "2. 如果问题超出职责范围，回复：「抱歉，我是一个智能运维助手，专注于运维和技术问题。」\n" +
                "3. 回答要专业、准确、简洁\n\n" +
                "【示例】\n" +
                "用户问：今天天气怎么样？\n" +
                "助手答：抱歉，我是一个智能运维助手，专注于运维和技术问题。请问有什么运维相关的问题我可以帮您解答？";
    }

    /**
     * 构建RAG提示词
     * @param question 用户问题
     * @param context 上下文内容
     * @return 消息列表
     */
    public List<Message> buildRagPrompt(String question, String context) {
        String content = String.format(
                "【用户问题】\n%s\n\n【相关上下文】\n%s\n\n请根据上下文回答用户问题。",
                question, context);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("你是一个专业的知识库助手，请根据提供的上下文信息回答用户问题。")
                .build());
        messages.add(Message.builder()
                .role(Role.USER.getValue())
                .content(content)
                .build());

        return messages;
    }

    /**
     * 构建历史消息列表
     * @param history 历史记录
     * @return 消息列表
     */
    private List<Message> buildHistoryMessages(List<Map<String, String>> history) {
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (role != null && content != null) {
                    messages.add(Message.builder()
                            .role(Role.USER.getValue().equals(role) ? Role.USER.getValue() : Role.ASSISTANT.getValue())
                            .content(content)
                            .build());
                }
            }
        }
        return messages;
    }

    /**
     * 构建React Agent系统提示词
     * @param history 对话历史
     * @return 系统提示词
     */
    public String buildReactAgentPrompt(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个智能运维助手。\n");

        sb.append("可用工具：\n");
        sb.append("- getCurrentTime: 获取当前时间\n");
        sb.append("- searchKnowledgeBase: 检索知识库文档\n");
        sb.append("- queryMetrics: 查询系统监控指标\n");
        sb.append("- queryLogs: 查询腾讯云日志\n\n");

        sb.append("注意：\n");
        sb.append("- 如果不需要工具可以直接回答\n");
        sb.append("- 回答问题时请使用中文\n");

        return sb.toString();
    }
}