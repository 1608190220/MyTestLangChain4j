package com.example.langchain.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Skill 注册中心 - 管理所有内置和自定义 Skill
 */
@Component
public class SkillRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);
    
    private final List<Skill> skills = new CopyOnWriteArrayList<>();
    
    // Spring 会自动注入所有 Skill 类型的 Bean
    @Autowired(required = false)
    private List<Skill> skillBeans = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        logger.debug("[SkillRegistry] 开始初始化 Skill 注册中心");
        
        // 自动注册所有被 @Component 标注的 Skill
        if (skillBeans != null) {
            logger.debug("[SkillRegistry] 发现 {} 个 Skill Bean", skillBeans.size());
            for (Skill skill : skillBeans) {
                register(skill);
            }
        } else {
            logger.debug("[SkillRegistry] 未发现任何 Skill Bean");
        }
        
        // 初始化时注册额外的内置 Skill
        registerBuiltInSkills();
        
        logger.debug("[SkillRegistry] Skill 注册中心初始化完成，共注册 {} 个 Skill", skills.size());
    }
    
    /**
     * 注册 Skill
     */
    public void register(Skill skill) {
        // 避免重复注册
        if (skills.stream().anyMatch(s -> s.getName().equals(skill.getName()))) {
            logger.debug("[SkillRegistry] Skill '{}' 已存在，跳过注册", skill.getName());
            return;
        }
        skills.add(skill);
        // 按优先级排序
        skills.sort(Comparator.comparingInt(Skill::getPriority));
        logger.debug("[SkillRegistry] 成功注册 Skill: '{}' (优先级: {}, 描述: {})", 
                skill.getName(), skill.getPriority(), skill.getDescription());
    }
    
    /**
     * 注销 Skill
     */
    public void unregister(Skill skill) {
        skills.remove(skill);
        logger.debug("[SkillRegistry] 注销 Skill: '{}'", skill.getName());
    }
    
    /**
     * 获取所有已注册的 Skill
     */
    public List<Skill> getAllSkills() {
        logger.debug("[SkillRegistry] 获取所有 Skill，当前共 {} 个", skills.size());
        return new ArrayList<>(skills);
    }
    
    /**
     * 根据名称查找 Skill
     */
    public Skill findSkillByName(String name) {
        logger.debug("[SkillRegistry] 查找 Skill: '{}'", name);
        Skill skill = skills.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (skill != null) {
            logger.debug("[SkillRegistry] 找到 Skill: '{}'", name);
        } else {
            logger.debug("[SkillRegistry] 未找到 Skill: '{}'", name);
        }
        return skill;
    }
    
    /**
     * 查找可以处理该消息的 Skill
     */
    public List<Skill> findSkillsCanHandle(String message) {
        logger.debug("[SkillRegistry] 查找可以处理消息的 Skill，消息: '{}'", message);
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills) {
            boolean canHandle = skill.canHandle(message);
            logger.debug("[SkillRegistry] Skill '{}' canHandle: {}", skill.getName(), canHandle);
            if (canHandle) {
                result.add(skill);
            }
        }
        logger.debug("[SkillRegistry] 找到 {} 个可以处理消息的 Skill: {}",
                result.size(), result.stream().map(Skill::getName).collect(Collectors.toList()));
        return result;
    }
    
    /**
     * 注册内置 Skill
     * 子类可以重写此方法添加更多内置 Skill
     */
    protected void registerBuiltInSkills() {
        logger.debug("[SkillRegistry] 开始注册内置 Skill");
        // 默认实现，可以在这里注册一些基础 Skill
        // 例如：register(new TimeSkill());
        //       register(new CalculatorSkill());
    }
}