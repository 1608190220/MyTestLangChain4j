package com.example.langchain.controller;

import com.example.langchain.skill.Skill;
import com.example.langchain.service.impl.ChatServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill 管理控制器 - 用于查看和管理内置 Skill
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {
    
    private final ChatServiceImpl chatService;
    
    @Autowired
    public SkillController(ChatServiceImpl chatService) {
        this.chatService = chatService;
    }
    
    /**
     * 获取所有已注册的 Skill
     */
    @GetMapping
    public List<Map<String, Object>> getAllSkills() {
        List<Skill> skills = chatService.getAllSkills();
        return skills.stream().map(skill -> {
            Map<String, Object> info = new HashMap<>();
            info.put("name", skill.getName());
            info.put("description", skill.getDescription());
            info.put("priority", skill.getPriority());
            return info;
        }).collect(Collectors.toList());
    }
    
    /**
     * 测试 Skill 是否匹配消息
     */
    @PostMapping("/test")
    public Map<String, Object> testSkill(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        List<Skill> skills = chatService.getAllSkills();
        
        List<String> matchedSkills = skills.stream()
                .filter(skill -> skill.canHandle(message))
                .map(Skill::getName)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("matchedSkills", matchedSkills);
        result.put("totalSkills", skills.size());
        return result;
    }
}