package com.example.langchain.service.impl;

import com.example.langchain.service.ChatService;
import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillRegistry;
import com.example.langchain.skill.SkillResult;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    // 从配置文件注入参数
    @Value("${app.chat.api-timeout-seconds:30}")
    private int apiTimeoutSeconds;

    @Value("${app.chat.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.chat.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private double temperature;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:2000}")
    private int maxTokens;

    @Value("${langchain4j.open-ai.chat-model.timeout:60}")
    private int modelTimeoutSeconds;

    private OpenAiChatModel chatModel;
    private final SkillRegistry skillRegistry;
    private final ExecutorService executorService;

    @Autowired
    public ChatServiceImpl(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @PostConstruct
    public void init() {
        logger.debug("[ChatService] PostConstruct 初始化");
        
        // 验证 API Key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.error("[ChatService] API Key 未配置！请设置 OPENAI_API_KEY 环境变量");
        } else {
            logger.info("[ChatService] API Key 已配置 (长度: {})", apiKey.length());
        }
        
        // 初始化 ChatModel
        initializeChatModel();
        
        logger.info("[ChatService] ChatServiceImpl 初始化完成，API 超时配置: {}秒", apiTimeoutSeconds);
    }

    /**
     * 初始化 ChatModel
     */
    private void initializeChatModel() {
        try {
            OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofSeconds(modelTimeoutSeconds));

            this.chatModel = builder.build();
            
            logger.info("[ChatService] ChatModel 初始化成功 - 模型: {}, BaseURL: {}", 
                    modelName, baseUrl);
        } catch (Exception e) {
            logger.error("[ChatService] ChatModel 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("ChatModel 初始化失败", e);
        }
    }

    @Override
    public String chat(String message) {
        logger.debug("[ChatService] 收到聊天请求，消息: '{}'", message);

        // 检查 API Key 是否配置
        if (chatModel == null) {
            return "⚠️ AI 服务暂未配置，无法使用智能对话功能。\n\n" +
                   "如需使用 AI 功能，请联系网站管理员进行配置。\n\n" +
                   "您目前仍可以使用以下内置功能：\n" +
                   "• 查询时间：\"现在几点\"\n" +
                   "• 数学计算：\"1+2\" 或 \"100的阶乘\"\n" +
                   "• 保存文档：\"保存为 markdown：内容...\"";
        }

        // 创建上下文
        SkillContext context = new SkillContext();
        logger.debug("[ChatService] 创建 SkillContext，sessionId: '{}'", context.getSessionId());

        // 1. 先尝试使用 Skill 处理
        List<Skill> matchedSkills = skillRegistry.findSkillsCanHandle(message);
        logger.debug("[ChatService] 找到 {} 个匹配的 Skill", matchedSkills.size());

        for (Skill skill : matchedSkills) {
            logger.debug("[ChatService] 开始执行 Skill: '{}'", skill.getName());
            try {
                SkillResult result = skill.execute(message, context);
                logger.debug("[ChatService] Skill '{}' 执行结果 - success: {}, shouldContinueToAI: {}",
                        skill.getName(), result.isSuccess(), result.shouldContinueToAI());

                if (!result.isSuccess()) {
                    logger.debug("[ChatService] Skill '{}' 执行失败: {}",
                            skill.getName(), result.getErrorMessage());
                    continue;
                }

                if (!result.shouldContinueToAI()) {
                    logger.debug("[ChatService] Skill '{}' 直接返回结果，不调用 AI", skill.getName());
                    logger.debug("[ChatService] 返回结果: '{}'", result.getResult());
                    return result.getResult();
                }

                if (result.getResult() != null) {
                    context.setAttribute(skill.getName() + "_result", result.getResult());
                    logger.debug("[ChatService] Skill '{}' 结果已加入上下文", skill.getName());
                }

            } catch (Exception e) {
                logger.debug("[ChatService] Skill '{}' 执行异常: {}", skill.getName(), e.getMessage());
            }
        }

        // 2. 调用 AI 模型（带重试机制和超时控制）
        String enhancedMessage = buildEnhancedPrompt(message, context);
        logger.debug("[ChatService] 构建增强提示词，长度: {}", enhancedMessage.length());

        return callAIWithRetryAndTimeout(enhancedMessage);
    }

    @Override
    public Flux<String> chatStream(String message) {
        logger.debug("[ChatService] 收到流式聊天请求，消息: '{}'", message);

        // 检查 API Key 是否配置
        if (chatModel == null) {
            String errorMsg = "⚠️ AI 服务暂未配置，无法使用智能对话功能。\n\n" +
                   "如需使用 AI 功能，请联系网站管理员进行配置。\n\n" +
                   "您目前仍可以使用以下内置功能：\n" +
                   "• 查询时间：\"现在几点\"\n" +
                   "• 数学计算：\"1+2\" 或 \"100的阶乘\"\n" +
                   "• 保存文档：\"保存为 markdown：内容...\"";
            return streamText(errorMsg);
        }

        return Flux.defer(() -> {
            SkillContext context = new SkillContext();
            logger.debug("[ChatService] 创建 SkillContext，sessionId: '{}'", context.getSessionId());

            // 1. 先尝试使用 Skill 处理
            List<Skill> matchedSkills = skillRegistry.findSkillsCanHandle(message);
            logger.debug("[ChatService] 找到 {} 个匹配的 Skill", matchedSkills.size());

            for (Skill skill : matchedSkills) {
                logger.debug("[ChatService] 开始执行 Skill: '{}'", skill.getName());
                try {
                    SkillResult result = skill.execute(message, context);
                    logger.debug("[ChatService] Skill '{}' 执行结果 - success: {}, shouldContinueToAI: {}",
                            skill.getName(), result.isSuccess(), result.shouldContinueToAI());

                    if (!result.isSuccess()) {
                        logger.debug("[ChatService] Skill '{}' 执行失败: {}",
                                skill.getName(), result.getErrorMessage());
                        continue;
                    }

                    if (!result.shouldContinueToAI()) {
                        logger.debug("[ChatService] Skill '{}' 直接返回结果，逐字输出", skill.getName());
                        return streamText(result.getResult());
                    }

                    if (result.getResult() != null) {
                        context.setAttribute(skill.getName() + "_result", result.getResult());
                    }

                } catch (Exception e) {
                    logger.debug("[ChatService] Skill '{}' 执行异常: {}", skill.getName(), e.getMessage());
                }
            }

            // 2. 调用 AI 模型（带重试机制和超时控制）
            String enhancedMessage = buildEnhancedPrompt(message, context);
            logger.debug("[ChatService] 构建增强提示词，长度: {}", enhancedMessage.length());

            return callAIWithRetryAndTimeoutStream(enhancedMessage);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 带重试和超时机制的 AI 调用（同步）
     */
    private String callAIWithRetryAndTimeout(String message) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetryAttempts) {
            attempt++;
            logger.debug("[ChatService] 第 {} 次尝试调用 AI 模型", attempt);

            try {
                // 使用 CompletableFuture 实现超时控制
                Future<String> future = executorService.submit(() -> chatModel.chat(message));
                String aiResponse = future.get(apiTimeoutSeconds, TimeUnit.SECONDS);
                
                logger.debug("[ChatService] AI 模型返回结果，长度: {}", aiResponse.length());

                if (attempt > 1) {
                    logger.info("[ChatService] AI 调用在第 {} 次重试后成功", attempt);
                }
                return aiResponse;

            } catch (TimeoutException e) {
                lastException = new RuntimeException("API 调用超时（" + apiTimeoutSeconds + "秒）");
                logger.warn("[ChatService] 第 {} 次 AI 调用超时", attempt);
                
                if (attempt >= maxRetryAttempts) {
                    break;
                }
                
                // 等待后重试
                sleepBeforeRetry(attempt);
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("[ChatService] 第 {} 次 AI 调用失败: {}", attempt, e.getMessage());

                // 判断是否可重试
                if (!isRetryableException(e) || attempt >= maxRetryAttempts) {
                    break;
                }

                // 等待后重试
                sleepBeforeRetry(attempt);
            }
        }

        // 所有重试都失败，返回友好错误信息
        return handleAIException(lastException);
    }

    /**
     * 带重试和超时机制的 AI 调用（流式）
     */
    private Flux<String> callAIWithRetryAndTimeoutStream(String message) {
        return Flux.defer(() -> {
            int attempt = 0;
            Exception lastException = null;

            while (attempt < maxRetryAttempts) {
                attempt++;
                logger.debug("[ChatService] 第 {} 次尝试调用 AI 模型（流式）", attempt);

                try {
                    // 使用 CompletableFuture 实现超时控制
                    Future<String> future = executorService.submit(() -> chatModel.chat(message));
                    String aiResponse = future.get(apiTimeoutSeconds, TimeUnit.SECONDS);
                    
                    logger.debug("[ChatService] AI 模型返回结果，长度: {}", aiResponse.length());

                    if (attempt > 1) {
                        logger.info("[ChatService] AI 调用在第 {} 次重试后成功", attempt);
                    }
                    return streamText(aiResponse);

                } catch (TimeoutException e) {
                    lastException = new RuntimeException("API 调用超时（" + apiTimeoutSeconds + "秒）");
                    logger.warn("[ChatService] 第 {} 次 AI 调用超时（流式）", attempt);
                    
                    if (attempt >= maxRetryAttempts) {
                        break;
                    }
                    
                    sleepBeforeRetry(attempt);
                    
                } catch (Exception e) {
                    lastException = e;
                    logger.warn("[ChatService] 第 {} 次 AI 调用失败（流式）: {}", attempt, e.getMessage());

                    if (!isRetryableException(e) || attempt >= maxRetryAttempts) {
                        break;
                    }

                    sleepBeforeRetry(attempt);
                }
            }

            // 返回错误信息流
            return streamText(handleAIException(lastException));
        });
    }

    /**
     * 重试前等待
     */
    private void sleepBeforeRetry(int attempt) {
        logger.debug("[ChatService] 等待 {} 毫秒后重试", retryDelayMs * attempt);
        try {
            Thread.sleep(retryDelayMs * attempt);  // 指数退避
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 判断是否可重试的异常
     */
    private boolean isRetryableException(Exception e) {
        if (e instanceof HttpException) {
            String message = e.getMessage();
            // 引擎过载、超时等临时性问题可以重试
            return message != null && (
                    message.contains("engine_overloaded") ||
                    message.contains("timeout") ||
                    message.contains("rate_limit") ||
                    message.contains("temporarily unavailable")
            );
        }
        // 网络超时异常也可以重试
        return e instanceof SocketTimeoutException ||
               (e.getCause() != null && e.getCause() instanceof SocketTimeoutException);
    }

    /**
     * 处理 AI 异常，返回友好提示
     */
    private String handleAIException(Exception e) {
        logger.error("[ChatService] AI 服务调用失败（已重试 {} 次）: {}", maxRetryAttempts, e.getMessage(), e);

        if (e instanceof HttpException) {
            String message = e.getMessage();

            // 引擎过载
            if (message != null && message.contains("engine_overloaded")) {
                return "🤖 AI 服务当前繁忙，请稍后再试。您可以先尝试使用内置的 Skill 功能，如：\n" +
                       "• 查询时间：\"现在几点\"\n" +
                       "• 计算：\"1+2\" 或 \"5的阶乘\"\n" +
                       "• 斐波那契：\"斐波那契第10项\"";
            }

            // 超时错误
            if (message != null && (message.contains("timeout") || message.contains("SocketTimeout"))) {
                return "⏱️ 请求超时，AI 服务响应较慢。建议：\n" +
                       "1. 简化您的问题\n" +
                       "2. 使用内置 Skill 功能（如 \"现在几点\"）\n" +
                       "3. 稍后重试";
            }

            // 速率限制
            if (message != null && message.contains("rate_limit")) {
                return "🚦 请求过于频繁，请稍后再试。您可以先使用内置的 Skill 功能。";
            }

            // 其他 HTTP 错误
            return "抱歉，AI 服务暂时不可用。错误信息：" + message;
        }

        // 超时异常
        if (e.getMessage() != null && e.getMessage().contains("超时")) {
            return "⏱️ 请求超时（" + apiTimeoutSeconds + "秒），AI 服务响应较慢。建议：\n" +
                   "1. 简化您的问题\n" +
                   "2. 使用内置 Skill 功能（如 \"现在几点\"）\n" +
                   "3. 稍后重试";
        }

        // 其他异常
        return "抱歉，服务暂时不可用，请稍后重试。错误信息：" + e.getMessage();
    }

    /**
     * 将文本转换为流式输出
     */
    private Flux<String> streamText(String text) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromArray(text.split(""))
                .delayElements(Duration.ofMillis(30))
                .concatWith(Flux.just("[DONE]"));
    }

    /**
     * 构建增强的提示词
     */
    private String buildEnhancedPrompt(String originalMessage, SkillContext context) {
        StringBuilder prompt = new StringBuilder();

        if (!context.getAttributes().isEmpty()) {
            prompt.append("【系统上下文信息】\n");
            context.getAttributes().forEach((key, value) -> {
                prompt.append(key).append(": ").append(value).append("\n");
            });
            prompt.append("\n");
        }

        prompt.append("【用户问题】\n");
        prompt.append(originalMessage);

        return prompt.toString();
    }

    /**
     * 注册自定义 Skill
     */
    public void registerSkill(Skill skill) {
        logger.debug("[ChatService] 注册自定义 Skill: '{}'", skill.getName());
        skillRegistry.register(skill);
    }

    /**
     * 获取所有已注册的 Skill
     */
    public List<Skill> getAllSkills() {
        logger.debug("[ChatService] 获取所有已注册的 Skill");
        return skillRegistry.getAllSkills();
    }
}
