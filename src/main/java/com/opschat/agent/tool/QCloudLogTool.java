package com.opschat.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 腾讯云日志查询工具
 * 使用 Spring AI @Tool 注解规范
 */
@Slf4j
@Component
public class QCloudLogTool {

    /**
     * 是否启用真实API调用
     */
    @Value("${opschat.tool.log.enabled:false}")
    private boolean enabled;

    /**
     * 模拟日志数据
     */
    private static final String SIMULATED_LOG = """
            2024-01-15 10:23:45 [INFO] [OrderService] - 订单创建成功, orderId=ORD20240115001, amount=1299.00
            2024-01-15 10:23:46 [DEBUG] [PaymentService] - 支付请求已发送, orderId=ORD20240115001
            2024-01-15 10:23:47 [INFO] [PaymentService] - 支付成功, transactionId=TXN88273619273
            2024-01-15 10:24:01 [WARN] [InventoryService] - 库存不足警告, productId=PRD001, remaining=5
            2024-01-15 10:24:15 [ERROR] [DeliveryService] - 配送接口调用失败, retryCount=3
            2024-01-15 10:25:00 [INFO] [DeliveryService] - 配送任务已创建, trackingNo=SF1234567890
            """;

    /**
     * 查询腾讯云日志
     * @param query 查询关键词
     * @param timeRange 时间范围（如：today, yesterday, last_hour）
     * @return 日志查询结果
     */
    @Tool(description = "查询腾讯云日志")
    public String queryLogs(String query, String timeRange) {
        log.info("[QCloudLogTool] 执行日志查询: query={}, timeRange={}", query, timeRange);

        if (!enabled) {
            log.warn("[QCloudLogTool] 日志工具未启用，返回模拟数据");
            return buildSimulatedResult(query, timeRange);
        }

        // 实际生产环境中，这里会调用腾讯云 CLS SDK
        // 由于未配置真实凭证，返回模拟数据
        return buildSimulatedResult(query, timeRange);
    }

    /**
     * 构建模拟结果
     */
    private String buildSimulatedResult(String query, String timeRange) {
        StringBuilder result = new StringBuilder();
        result.append("日志查询结果:\n");
        result.append("────────────────────────────────────────\n");
        result.append("查询条件: ").append(query != null ? query : "全部").append("\n");
        result.append("时间范围: ").append(timeRange != null ? timeRange : "最近1小时").append("\n");
        result.append("────────────────────────────────────────\n");
        result.append(SIMULATED_LOG);
        result.append("────────────────────────────────────────\n");
        result.append("提示: 完整日志功能需要配置腾讯云 CLS 凭证");
        return result.toString();
    }
}