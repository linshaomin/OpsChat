package com.opschat.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Slf4j
@Service
public class SessionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_PREFIX = "chat:session:";

    @Value("${session.expire-hours:24}")
    private long sessionExpireHours;

    @Value("${session.max-window-size:30}")
    private int maxWindowSize;

    @Value("${session.max-tokens:8192}")
    private int maxTokens;

    @Value("${session.summary-threshold:0.7}")
    private double summaryThreshold;

    /**
     * 获取或创建会话
     */
    public SessionInfo getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }
        String key = getSessionKey(sessionId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            redisTemplate.expire(key, sessionExpireHours, TimeUnit.HOURS);
            return (SessionInfo) obj;
        }
        SessionInfo session = new SessionInfo(sessionId);
        saveSession(session);
        return session;
    }

    /**
     * 保存会话
     */
    public void saveSession(SessionInfo session) {
        redisTemplate.opsForValue().set(
            getSessionKey(session.getSessionId()),
            session,
            sessionExpireHours, TimeUnit.HOURS
        );
    }

    /**
     * 获取会话
     */
    public SessionInfo getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        String key = getSessionKey(sessionId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            redisTemplate.expire(key, sessionExpireHours, TimeUnit.HOURS);
            return (SessionInfo) obj;
        }
        return null;
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.delete(getSessionKey(sessionId)));
    }

    /**
     * 获取所有会话ID
     */
    public List<String> getAllSessionIds() {
        Set<String> keys = redisTemplate.keys(SESSION_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return new ArrayList<>();
        return keys.stream().map(k -> k.substring(SESSION_PREFIX.length())).collect(Collectors.toList());
    }

    /**
     * 获取所有会话信息
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
     */
    public List<Map<String, String>> getHistoryMessages(String sessionId) {
        SessionInfo session = getOrCreateSession(sessionId);
        return session.getMessageHistory();
    }

    /**
     * 添加消息到会话（包含窗口管理和摘要逻辑）
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

        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiAnswer);
        messageHistory.add(assistantMsg);

        // 窗口大小限制
        int maxMessages = maxWindowSize * 2;
        while (messageHistory.size() > maxMessages) {
            messageHistory.remove(0);
            if (!messageHistory.isEmpty()) messageHistory.remove(0);
        }

        // 摘要触发检查
        int currentTokens = calculateTokens(messageHistory);
        double ratio = (double) currentTokens / maxTokens;
        
        if (ratio >= summaryThreshold && messageHistory.size() > 4) {
            log.info("触发历史摘要，当前 token: {}, 阈值: {}", currentTokens, maxTokens * summaryThreshold);
            List<Map<String, String>> summarized = summarizeHistory(messageHistory);
            session.setMessageHistory(summarized);
            messageHistory = summarized;
        }

        // Token 大小限制
        currentTokens = calculateTokens(messageHistory);
        while (currentTokens > maxTokens && messageHistory.size() > 2) {
            messageHistory.remove(0);
            if (!messageHistory.isEmpty()) messageHistory.remove(0);
            currentTokens = calculateTokens(messageHistory);
        }
    }

    private String getSessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

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

    @Data
    public static class SessionInfo {
        private String sessionId;
        private List<Map<String, String>> messageHistory;
        private long createTime;

        public SessionInfo() {
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
        }

        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
        }

        public void clearHistory() {
            if (messageHistory != null) messageHistory.clear();
        }

        @JsonIgnore
        public int getMessagePairCount() {
            return messageHistory != null ? messageHistory.size() / 2 : 0;
        }

        @JsonIgnore
        public String getTitle() {
            if (messageHistory == null || messageHistory.isEmpty()) {
                return "新对话";
            }
            for (Map<String, String> msg : messageHistory) {
                if ("user".equals(msg.get("role"))) {
                    String content = msg.get("content");
                    if (content != null && !content.isEmpty()) {
                        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
                    }
                }
            }
            return "新对话";
        }
    }
}
