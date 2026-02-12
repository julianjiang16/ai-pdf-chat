package com.tencent.mdax.ai_pdf_chat.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {
    
    // 使用内存存储对话历史，key为sessionId
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    
    // 最大历史消息数量
    private static final int MAX_HISTORY_SIZE = 10;
    
    public static class ChatMessage {
        private final String role; // "user" 或 "assistant"
        private final String content;
        private final long timestamp;
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRole() { return role; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 添加消息到对话历史
     */
    public void addMessage(String sessionId, String role, String content) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new ChatMessage(role, content));
        
        // 限制历史消息数量
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0); // 移除最旧的消息
        }
    }
    
    /**
     * 获取对话历史
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return chatHistories.getOrDefault(sessionId, new ArrayList<>());
    }
    
    /**
     * 清空对话历史
     */
    public void clearHistory(String sessionId) {
        chatHistories.remove(sessionId);
    }
    
    /**
     * 构建上下文提示词
     */
    public String buildContextPrompt(String sessionId, String currentQuestion) {
        List<ChatMessage> history = getHistory(sessionId);
        if (history.isEmpty()) {
            return currentQuestion;
        }
        
        StringBuilder context = new StringBuilder();
        context.append("以下是之前的对话历史，请参考这些信息回答当前问题：\n\n");
        
        for (ChatMessage message : history) {
            context.append(message.getRole().equals("user") ? "用户: " : "助手: ");
            context.append(message.getContent());
            context.append("\n");
        }
        
        context.append("\n当前问题：");
        context.append(currentQuestion);
        
        return context.toString();
    }
}