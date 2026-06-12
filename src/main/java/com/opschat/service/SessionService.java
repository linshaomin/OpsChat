package com.opschat.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opschat.config.SessionProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话服务类
 * 管理会话生命周期、消息历史和摘要压缩
 */
@Slf4j
@Service
public class SessionService {

    private static final String SESSION_PREFIX = "chat:session:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionProperties sessionProperties;

    /**
     * 构造函数注入
     * @param redisTemplate Redis模板
     * @param sessionProperties 会话配置
     */
    public SessionService(RedisTemplate<String, Object> redisTemplate,
                         SessionProperties sessionProperties) {
        this.redisTemplate = redisTemplate;
        this.sessionProperties = sessionProperties;
    }

    /**
     * 获取或创建会话
     * @param sessionId 会话ID，为空时自动生成
     * @return 会话信息
     */
    public SessionInfo getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }
        String key = getSessionKey(sessionId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            refreshSessionExpire(key);
            return (SessionInfo) obj;
        }
        SessionInfo session = new SessionInfo(sessionId);
        saveSession(session);
        return session;
    }

    /**
     * 保存会话
     * @param session 会话信息
     */
    public void saveSession(SessionInfo session) {
        redisTemplate.opsForValue().set(
            getSessionKey(session.getSessionId()),
            session,
            sessionProperties.getExpireHours(), TimeUnit.HOURS
        );
    }

    /**
     * 获取会话
     * @param sessionId 会话ID
     * @return 会话信息，不存在返回null
     */
    public SessionInfo getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        String key = getSessionKey(sessionId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            refreshSessionExpire(key);
            return (SessionInfo) obj;
        }
        return null;
    }

    /**
     * 删除会话
     * @param sessionId 会话ID
     * @return 删除是否成功
     */
    public boolean deleteSession(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.delete(getSessionKey(sessionId)));
    }

    /**
     * 获取所有会话ID
     * @return 会话ID列表
     */
    public List<String> getAllSessionIds() {
        Set<String> keys = redisTemplate.keys(SESSION_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return new ArrayList<>();
        return keys.stream().map(k -> k.substring(SESSION_PREFIX.length())).collect(Collectors.toList());
    }

    /**
     * 获取所有会话信息（按创建时间倒序）
     * @return 会话信息列表
     */
    public List<SessionInfo> getAllSessions() {
        List<String> sessionIds = getAllSessionIds();
        List<SessionInfo> sessions = new ArrayList<>();
        
        for (String sessionId : sessionIds) {
            SessionInfo session = getSession(sessionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        
        sessions.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return sessions;
    }

    /**
     * 获取历史消息列表
     * @param sessionId 会话ID
     * @return 消息历史列表
     */
    public List<Map<String, String>> getHistoryMessages(String sessionId) {
        SessionInfo session = getOrCreateSession(sessionId);
        return session.getMessageHistory();
    }

    /**
     * 添加消息到会话（包含窗口管理和摘要逻辑）
     * @param session 会话信息
     * @param userQuestion 用户问题
     * @param aiAnswer AI回答
     */
    public void addMessage(SessionInfo session, String userQuestion, String aiAnswer) {
        List<Map<String, String>> messageHistory = session.getMessageHistory();
        if (messageHistory == null) {
            messageHistory = new ArrayList<>();
            session.setMessageHistory(messageHistory);
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userQuestion);
        messageHistory.add(userMsg);

        // 如果是第一条消息，更新会话标题
        if (messageHistory.size() == 1) {
            session.updateTitle(userQuestion);
        }

        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiAnswer);
        messageHistory.add(assistantMsg);

        // 窗口大小限制
        maintainWindowSize(messageHistory);

        // 摘要触发检查
        int currentTokens = calculateTokens(messageHistory);
        double ratio = (double) currentTokens / sessionProperties.getMaxTokens();
        
        if (ratio >= sessionProperties.getSummaryThreshold() && messageHistory.size() > 4) {
            log.info("触发历史摘要，当前 token: {}, 阈值: {}", currentTokens, 
                    sessionProperties.getMaxTokens() * sessionProperties.getSummaryThreshold());
            List<Map<String, String>> summarized = summarizeHistory(messageHistory);
            session.setMessageHistory(summarized);
            messageHistory = summarized;
        }

        // Token 大小限制（二次检查）
        enforceTokenLimit(messageHistory);
    }

    /**
     * 刷新会话过期时间
     */
    private void refreshSessionExpire(String key) {
        redisTemplate.expire(key, sessionProperties.getExpireHours(), TimeUnit.HOURS);
    }

    /**
     * 维护窗口大小
     */
    private void maintainWindowSize(List<Map<String, String>> messageHistory) {
        int maxMessages = sessionProperties.getMaxWindowSize() * 2;
        while (messageHistory.size() > maxMessages) {
            messageHistory.remove(0);
            if (!messageHistory.isEmpty()) {
                messageHistory.remove(0);
            }
        }
    }

    /**
     * 强制执行Token限制
     */
    private void enforceTokenLimit(List<Map<String, String>> messageHistory) {
        int currentTokens = calculateTokens(messageHistory);
        while (currentTokens > sessionProperties.getMaxTokens() && messageHistory.size() > 2) {
            messageHistory.remove(0);
            if (!messageHistory.isEmpty()) {
                messageHistory.remove(0);
            }
            currentTokens = calculateTokens(messageHistory);
        }
    }

    private String getSessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    /**
     * 计算Token数量（简化估算）
     */
    private int calculateTokens(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        
        int total = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                int chineseChars = content.replaceAll("[\\x00-\\x7F]", "").length();
                int englishWords = content.split("[\\s]+").length;
                total += (int)(chineseChars * 1.3) + englishWords + 4;
            }
        }
        return total;
    }

    /**
     * 历史摘要（保留最新消息，压缩历史）
     */
    private List<Map<String, String>> summarizeHistory(List<Map<String, String>> messages) {
        if (messages == null || messages.size() <= 4) {
            return messages;
        }

        try {
            int halfSize = messages.size() / 2;
            List<Map<String, String>> historyToSummarize = messages.subList(0, halfSize);
            
            StringBuilder historyText = new StringBuilder();
            for (Map<String, String> msg : historyToSummarize) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (role != null && content != null) {
                    historyText.append(role.equals("user") ? "用户: " : "助手: ");
                    historyText.append(content).append("\n");
                }
            }

            String summary = historyText.length() > 50 
                ? historyText.substring(0, 50) + "..." 
                : historyText.toString();
            
            List<Map<String, String>> result = new ArrayList<>();
            result.add(Map.of("role", "system", "content", "历史对话摘要：" + summary));
            result.addAll(messages.subList(halfSize, messages.size()));
            
            return result;
            
        } catch (Exception e) {
            log.warn("历史摘要失败: {}", e.getMessage());
            return messages;
        }
    }

    /**
     * 会话信息 DTO
     */
    @Data
    public static class SessionInfo {
        private String sessionId;
        private String title;
        private List<Map<String, String>> messageHistory;
        private long createTime;

        public SessionInfo() {
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
            this.title = generateDefaultTitle();
        }

        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
            this.title = generateDefaultTitle();
        }

        public void clearHistory() {
            if (messageHistory != null) {
                messageHistory.clear();
            }
            this.title = generateDefaultTitle();
        }

        /**
         * 更新会话标题
         * @param newTitle 新标题
         */
        public void updateTitle(String newTitle) {
            if (newTitle != null && !newTitle.isEmpty()) {
                this.title = newTitle.length() > 30 ? newTitle.substring(0, 30) + "..." : newTitle;
            }
        }

        /**
         * 删除指定索引的消息
         * @param index 消息索引
         * @return 是否删除成功
         */
        public boolean deleteMessage(int index) {
            if (messageHistory != null && index >= 0 && index < messageHistory.size()) {
                messageHistory.remove(index);
                return true;
            }
            return false;
        }

        @JsonIgnore
        public int getMessagePairCount() {
            return messageHistory != null ? messageHistory.size() / 2 : 0;
        }

        /**
         * 生成默认标题（基于时间）
         */
        private String generateDefaultTitle() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            return "对话 " + now.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        }
    }

    /**
     * 删除会话中的指定消息
     * @param sessionId 会话ID
     * @param messageIndex 消息索引
     * @return 是否删除成功
     */
    public boolean deleteMessage(String sessionId, int messageIndex) {
        SessionInfo session = getSession(sessionId);
        if (session != null) {
            boolean success = session.deleteMessage(messageIndex);
            if (success) {
                saveSession(session);
            }
            return success;
        }
        return false;
    }

    /**
     * 更新会话标题
     * @param sessionId 会话ID
     * @param title 新标题
     */
    public void updateSessionTitle(String sessionId, String title) {
        SessionInfo session = getSession(sessionId);
        if (session != null) {
            session.updateTitle(title);
            saveSession(session);
        }
    }
}