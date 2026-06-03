package com.opschat.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 路由结果类
 * 封装意图识别的结果
 */
@Data
@Builder
@AllArgsConstructor
public class RouteResult {

    /**
     * 工作流类型
     */
    private com.opschat.workflow.WorkflowType workflowType;

    /**
     * 置信度（0-1）
     */
    private float confidence;

    /**
     * 路由来源
     */
    private String source;

    /**
     * 路由原因说明
     */
    private String reason;

    /**
     * 是否需要澄清
     */
    private boolean needsClarification;

    /**
     * 澄清问题
     */
    private String clarifyQuestion;

    /**
     * 缺失的信息列表
     */
    private List<String> missingInfo;

    /**
     * 创建路由结果
     * @param workflowType 工作流类型
     * @param confidence 置信度
     * @param source 来源
     * @return 路由结果
     */
    public static RouteResult of(com.opschat.workflow.WorkflowType workflowType, float confidence, String source) {
        return RouteResult.builder()
                .workflowType(workflowType)
                .confidence(confidence)
                .source(source)
                .needsClarification(false)
                .build();
    }

    /**
     * 创建路由结果（带原因）
     */
    public static RouteResult of(com.opschat.workflow.WorkflowType workflowType, float confidence, String source, String reason) {
        RouteResult result = of(workflowType, confidence, source);
        result.setReason(reason);
        return result;
    }

    /**
     * 创建澄清路由结果
     * @param question 澄清问题
     * @param missingInfo 缺失信息列表
     * @param source 来源
     * @return 路由结果
     */
    public static RouteResult clarify(String question, List<String> missingInfo, String source) {
        return RouteResult.builder()
                .workflowType(com.opschat.workflow.WorkflowType.CLARIFY)
                .confidence(0f)
                .source(source)
                .needsClarification(true)
                .clarifyQuestion(question)
                .missingInfo(missingInfo)
                .build();
    }

    /**
     * 检查是否有效（置信度达标）
     * @param threshold 阈值
     * @return 是否有效
     */
    public boolean isValid(float threshold) {
        return workflowType != null && confidence >= threshold;
    }

    /**
     * 是否需要澄清
     * @return 是否需要澄清
     */
    public boolean requiresClarification() {
        return needsClarification || workflowType == com.opschat.workflow.WorkflowType.CLARIFY;
    }
}