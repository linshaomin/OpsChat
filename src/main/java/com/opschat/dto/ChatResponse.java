package com.opschat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应类型：content/tool_call/error/done
     */
    private String type;

    /**
     * 响应内容
     */
    private String content;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 创建内容响应
     */
    public static ChatResponse content(String content) {
        return ChatResponse.builder().type("content").content(content).build();
    }

    /**
     * 创建错误响应
     */
    public static ChatResponse error(String content) {
        return ChatResponse.builder().type("error").content(content).build();
    }

    /**
     * 创建完成响应
     */
    public static ChatResponse done() {
        return ChatResponse.builder().type("done").build();
    }
}