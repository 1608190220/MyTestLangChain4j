package com.example.langchain.skill;

/**
 * Skill 接口 - 定义可扩展的技能能力
 */
public interface Skill {
    
    /**
     * 获取 Skill 名称
     */
    String getName();
    
    /**
     * 获取 Skill 描述
     */
    String getDescription();
    
    /**
     * 判断是否可以处理该消息
     */
    boolean canHandle(String message);
    
    /**
     * 执行 Skill 逻辑
     * @param message 用户输入
     * @param context 上下文信息
     * @return Skill 执行结果
     */
    SkillResult execute(String message, SkillContext context);
    
    /**
     * 获取 Skill 优先级（数字越小优先级越高）
     */
    default int getPriority() {
        return 100;
    }
}