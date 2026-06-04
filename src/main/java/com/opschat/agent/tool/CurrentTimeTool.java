package com.opschat.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 当前时间工具
 * 使用 Spring AI @Tool 注解规范
 */
@Slf4j
@Component
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前时间
     * @return 当前时间字符串
     */
    @Tool(description = "获取当前时间")
    public String getCurrentTime() {
        log.info("[CurrentTimeTool] 执行工具调用");
        return LocalDateTime.now().format(FORMATTER);
    }
}