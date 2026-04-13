# Skill 系统使用指南

## 概述

本服务支持内置 Skill 扩展机制，允许你自定义业务逻辑并在 AI 处理之前或之后执行。

## Skill 工作原理

1. **优先级匹配**：所有已注册的 Skill 按优先级排序
2. **条件判断**：每个 Skill 通过 `canHandle()` 方法判断是否处理该消息
3. **执行逻辑**：匹配的 Skill 按优先级顺序执行
4. **结果处理**：
   - 如果 Skill 返回 `success()` - 直接返回结果，不调用 AI
   - 如果 Skill 返回 `successWithAI()` - 将结果加入上下文，继续调用 AI
   - 如果 Skill 返回 `passToAI()` - 继续调用 AI
   - 如果 Skill 返回 `failure()` - 记录错误，尝试下一个 Skill

## 如何创建自定义 Skill

### 1. 实现 Skill 接口

```java
package com.example.langchain.skill.impl;

import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillResult;
import org.springframework.stereotype.Component;

@Component
public class MyCustomSkill implements Skill {
    
    @Override
    public String getName() {
        return "MyCustomSkill";
    }
    
    @Override
    public String getDescription() {
        return "我的自定义 Skill 描述";
    }
    
    @Override
    public boolean canHandle(String message) {
        // 判断是否处理该消息
        return message.contains("关键词");
    }
    
    @Override
    public SkillResult execute(String message, SkillContext context) {
        // 执行业务逻辑
        
        // 示例：从上下文获取数据
        String sessionId = context.getSessionId();
        
        // 示例：设置上下文数据
        context.setAttribute("myData", "some value");
        
        // 返回结果
        return SkillResult.success("处理结果");
    }
    
    @Override
    public int getPriority() {
        return 20; // 数字越小优先级越高
    }
}
```

### 2. 注册 Skill

**方式一：Spring 自动扫描（推荐）**

在 Skill 类上添加 `@Component` 注解，Spring 会自动将其注册到 SkillRegistry。

**方式二：手动注册**

```java
@Autowired
private ChatServiceImpl chatService;

public void registerMySkill() {
    chatService.registerSkill(new MyCustomSkill());
}
```

## Skill 示例

### 时间查询 Skill

已内置，可以处理：
- "现在几点了？"
- "今天日期是多少？"
- "what time is it?"

### 计算器 Skill

```java
@Component
public class CalculatorSkill implements Skill {
    
    @Override
    public String getName() {
        return "CalculatorSkill";
    }
    
    @Override
    public String getDescription() {
        return "执行简单的数学计算";
    }
    
    @Override
    public boolean canHandle(String message) {
        // 匹配计算表达式，如：1+1, 2*3, 10/2
        return message.matches(".*\\d+\\s*[+\\-*/]\\s*\\d+.*");
    }
    
    @Override
    public SkillResult execute(String message, SkillContext context) {
        try {
            // 提取并计算表达式
            int result = calculate(message);
            return SkillResult.success("计算结果：" + result);
        } catch (Exception e) {
            return SkillResult.failure("计算失败：" + e.getMessage());
        }
    }
    
    private int calculate(String expression) {
        // 实现计算逻辑
        return 0;
    }
}
```

### 数据库查询 Skill

```java
@Component
public class DatabaseQuerySkill implements Skill {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public boolean canHandle(String message) {
        return message.contains("查询用户") || message.contains("用户信息");
    }
    
    @Override
    public SkillResult execute(String message, SkillContext context) {
        // 从消息中提取用户ID
        String userId = extractUserId(message);
        
        // 查询数据库
        User user = userRepository.findById(userId).orElse(null);
        
        if (user != null) {
            // 将查询结果加入上下文，让 AI 进一步处理
            context.setAttribute("userInfo", user);
            return SkillResult.successWithAI("已找到用户信息");
        }
        
        return SkillResult.failure("未找到用户");
    }
}
```

### API 调用 Skill

```java
@Component
public class WeatherSkill implements Skill {
    
    @Override
    public boolean canHandle(String message) {
        return message.contains("天气") || message.contains("temperature");
    }
    
    @Override
    public SkillResult execute(String message, SkillContext context) {
        // 调用天气 API
        String city = extractCity(message);
        WeatherInfo weather = callWeatherApi(city);
        
        return SkillResult.success(
            String.format("%s今天%s，温度%s°C", 
                city, weather.getCondition(), weather.getTemperature())
        );
    }
}
```

## API 接口

### 查看所有 Skill

```
GET /api/skills

响应：
[
  {
    "name": "TimeSkill",
    "description": "查询当前时间、日期等信息",
    "priority": 10
  }
]
```

### 测试 Skill 匹配

```
POST /api/skills/test
Content-Type: application/json

{
  "message": "现在几点了？"
}

响应：
{
  "message": "现在几点了？",
  "matchedSkills": ["TimeSkill"],
  "totalSkills": 1
}
```

### 聊天接口

```
GET /api/chat?message=现在几点了？

响应：
当前时间是：14:30:25
```

## 最佳实践

1. **优先级设计**：
   - 高频、简单的 Skill 设置较高优先级（数字小）
   - 复杂、耗时的 Skill 设置较低优先级

2. **错误处理**：
   - Skill 执行失败不应影响其他 Skill
   - 使用 try-catch 捕获异常

3. **上下文使用**：
   - 通过 SkillContext 传递数据
   - 多个 Skill 可以协作处理

4. **性能考虑**：
   - Skill 执行应快速完成
   - 耗时操作考虑异步处理

5. **安全性**：
   - 验证用户输入
   - 防止注入攻击
   - 敏感操作需要权限检查

## 扩展建议

你可以创建以下类型的 Skill：

- **业务查询 Skill**：查询订单、用户信息等
- **工具调用 Skill**：发送邮件、创建任务等
- **数据处理 Skill**：格式化数据、转换单位等
- **外部 API Skill**：天气、新闻、股票等
- **权限控制 Skill**：验证用户权限
- **日志记录 Skill**：记录操作日志
- **缓存 Skill**：缓存常用查询结果

通过 Skill 系统，你可以将业务逻辑与 AI 能力解耦，实现更灵活、更可控的智能应用。