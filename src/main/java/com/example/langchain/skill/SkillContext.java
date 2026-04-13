package com.example.langchain.skill;

import java.util.HashMap;
import java.util.Map;

/**
 * Skill 上下文 - 传递执行过程中的上下文信息
 */
public class SkillContext {
    
    private final Map<String, Object> attributes = new HashMap<>();
    private final String sessionId;
    private final long timestamp;
    
    public SkillContext() {
        this.sessionId = java.util.UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }
    
    public SkillContext(String sessionId) {
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
    }
    
    // 属性操作
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
    
    // Getters
    public String getSessionId() {
        return sessionId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
}