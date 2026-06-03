package com.opschat.workflow;

import com.opschat.router.IntentRouter;
import com.opschat.router.RouteResult;
import com.opschat.stream.WorkflowEvent;
import com.opschat.stream.WorkflowEventType;
import com.opschat.workflow.impl.ChatWorkflow;
import com.opschat.workflow.impl.KnowledgeWorkflow;
import com.opschat.workflow.impl.ReactWorkflow;
import com.opschat.workflow.impl.ToolWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流编排器
 * 根据路由结果选择并执行相应的工作流
 */
@Slf4j
@Component
public class WorkflowOrchestrator {

    /**
     * 工作流策略映射
     */
    private final Map<WorkflowType, WorkflowStrategy> workflowStrategyMap = new EnumMap<>(WorkflowType.class);

    /**
     * 意图路由器
     */
    private final IntentRouter intentRouter;

    /**
     * 构造函数
     * @param chatWorkflow 聊天工作流
     * @param knowledgeWorkflow 知识库工作流
     * @param toolWorkflow 工具工作流
     * @param reactWorkflow React工作流
     * @param intentRouter 意图路由器
     */
    public WorkflowOrchestrator(ChatWorkflow chatWorkflow,
                                 KnowledgeWorkflow knowledgeWorkflow,
                                 ToolWorkflow toolWorkflow,
                                 ReactWorkflow reactWorkflow,
                                 IntentRouter intentRouter) {
        this.intentRouter = intentRouter;
        registerWorkflow(chatWorkflow);
        registerWorkflow(knowledgeWorkflow);
        registerWorkflow(toolWorkflow);
        registerWorkflow(reactWorkflow);
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        log.info("[WorkflowOrchestrator] 初始化完成，已注册 {} 个工作流: {}", 
                workflowStrategyMap.size(), workflowStrategyMap.keySet());
    }

    /**
     * 注册工作流
     * @param workflow 工作流策略
     */
    private void registerWorkflow(WorkflowStrategy workflow) {
        workflowStrategyMap.put(workflow.getType(), workflow);
    }

    public String execute(String question, List<Map<String, String>> history) {
        log.info("[WorkflowOrchestrator] 执行工作流: {}", question);

        RouteResult routeResult = intentRouter.route(question, history);

        if (routeResult.requiresClarification()) {
            log.info("[WorkflowOrchestrator] 意图需要澄清: {}", question);
            return buildClarificationResponse(routeResult);
        }

        WorkflowType workflowType = routeResult.getWorkflowType();
        log.info("[WorkflowOrchestrator] 路由结果: {} -> {}", question, workflowType);

        WorkflowStrategy strategy = workflowStrategyMap.get(workflowType);
        if (strategy == null) {
            log.error("[WorkflowOrchestrator] 未找到工作流策略: {}", workflowType);
            return "抱歉，处理您的请求时出现错误。";
        }

        return strategy.execute(question, history);
    }

    public void executeStream(String question, List<Map<String, String>> history, Consumer<WorkflowEvent> eventConsumer) {
        // 执行意图路由
        RouteResult routeResult = intentRouter.route(question, history);

        // 需要澄清
        if (routeResult.requiresClarification()) {
            eventConsumer.accept(WorkflowEvent.content(buildClarificationResponse(routeResult)));
            eventConsumer.accept(WorkflowEvent.done());
            return;
        }

        // 获取工作流类型
        WorkflowType workflowType = routeResult.getWorkflowType();
        log.info("[WorkflowOrchestrator] 路由结果: {} -> {}", question, workflowType);

        // 获取并执行工作流
        WorkflowStrategy strategy = workflowStrategyMap.get(workflowType);
        if (strategy == null) {
            log.error("[WorkflowOrchestrator] 未找到工作流策略: {}", workflowType);
            eventConsumer.accept(WorkflowEvent.error("未找到工作流策略: " + workflowType));
            return;
        }

        // 执行工作流
        strategy.executeStream(question, history, eventConsumer);
    }

    /**
     * 构建澄清响应
     * @param routeResult 路由结果
     * @return 澄清消息
     */
    private String buildClarificationResponse(RouteResult routeResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(routeResult.getClarifyQuestion());
        
        // 添加缺失信息列表
        if (routeResult.getMissingInfo() != null && !routeResult.getMissingInfo().isEmpty()) {
            sb.append("\n\n请补充以下信息：");
            for (String info : routeResult.getMissingInfo()) {
                sb.append("\n• ").append(info);
            }
        }
        
        return sb.toString();
    }
}