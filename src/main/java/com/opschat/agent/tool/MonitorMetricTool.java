package com.opschat.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控指标查询工具
 * 使用 Spring AI @Tool 注解规范
 */
@Slf4j
@Component
public class MonitorMetricTool {

    /**
     * 模拟监控数据（实际应从监控系统获取）
     */
    private static final Map<String, Map<String, String>> METRIC_DATA = new ConcurrentHashMap<>();

    static {
        // 初始化模拟数据
        METRIC_DATA.put("cpu", Map.of(
                "avg", "45%",
                "max", "85%",
                "min", "12%",
                "unit", "%"
        ));
        METRIC_DATA.put("memory", Map.of(
                "used", "8.5GB",
                "total", "16GB",
                "percent", "53%",
                "unit", "GB"
        ));
        METRIC_DATA.put("disk", Map.of(
                "used", "120GB",
                "total", "500GB",
                "percent", "24%",
                "unit", "GB"
        ));
        METRIC_DATA.put("network", Map.of(
                "inbound", "150MB/s",
                "outbound", "80MB/s",
                "unit", "MB/s"
        ));
    }

    /**
     * 查询监控指标
     * @param metricType 指标类型（cpu/memory/disk/network）
     * @param timeRange 时间范围（可选）
     * @param service 服务名称（可选）
     * @return 指标查询结果
     */
    @Tool(description = "查询系统监控指标")
    public String queryMetrics(String metricType, String timeRange, String service) {
        log.info("[MonitorMetricTool] 执行监控指标查询: metricType={}, timeRange={}, service={}", 
                metricType, timeRange, service);

        if (metricType == null || metricType.trim().isEmpty()) {
            return "错误：指标类型不能为空";
        }

        Map<String, String> metrics = METRIC_DATA.get(metricType.toLowerCase());
        if (metrics == null) {
            return "未知的指标类型: " + metricType + "，支持的类型: cpu, memory, disk, network";
        }

        StringBuilder result = new StringBuilder();
        result.append("监控指标查询结果:\n");
        result.append("┌──────────┬─────────────┐\n");
        result.append("│ 指标项   │ 值          │\n");
        result.append("├──────────┼─────────────┤\n");
        
        metrics.forEach((key, value) -> {
            result.append(String.format("│ %-8s│ %-11s│\n", key, value));
        });
        
        result.append("└──────────┴─────────────┘\n");
        
        if (timeRange != null && !timeRange.isEmpty()) {
            result.append("时间范围: ").append(timeRange).append("\n");
        }
        if (service != null && !service.isEmpty()) {
            result.append("服务: ").append(service).append("\n");
        }

        return result.toString();
    }
}