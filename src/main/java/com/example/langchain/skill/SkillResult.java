package com.example.langchain.skill;

/**
 * Skill 执行结果
 */
public class SkillResult {
    
    private final boolean success;
    private final String result;
    private final String errorMessage;
    private final boolean shouldContinueToAI; // 是否继续调用AI模型
    
    private SkillResult(boolean success, String result, String errorMessage, boolean shouldContinueToAI) {
        this.success = success;
        this.result = result;
        this.errorMessage = errorMessage;
        this.shouldContinueToAI = shouldContinueToAI;
    }
    
    public static SkillResult success(String result) {
        return new SkillResult(true, result, null, false);
    }
    
    public static SkillResult successWithAI(String result) {
        return new SkillResult(true, result, null, true);
    }
    
    public static SkillResult failure(String errorMessage) {
        return new SkillResult(false, null, errorMessage, false);
    }
    
    public static SkillResult passToAI() {
        return new SkillResult(true, null, null, true);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getResult() {
        return result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public boolean shouldContinueToAI() {
        return shouldContinueToAI;
    }
}