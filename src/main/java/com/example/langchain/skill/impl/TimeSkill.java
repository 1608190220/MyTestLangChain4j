package com.example.langchain.skill.impl;

import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 时间查询 Skill - 示例实现
 * 可以处理与时间、日期相关的查询
 */
@Component
public class TimeSkill implements Skill {

    private static final Logger logger = LoggerFactory.getLogger(TimeSkill.class);

    // 纯时间查询模式 - 必须以时间词结尾或为核心
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "^(现在|当前|目前)?\\s*(几点|几时|时间|时刻|时分秒)$" +
        "|^(what|what's|whats)?\\s*(is)?\\s*(the)?\\s*(current)?\\s*time\\s*(now)?$",
        Pattern.CASE_INSENSITIVE
    );

    // 纯日期查询模式
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "^(今天|今日|当前|目前)?\\s*(日期|几号|几月几号|年月日)$" +
        "|^(what|what's|whats)?\\s*(is)?\\s*(the)?\\s*(current)?\\s*date\\s*(today)?$",
        Pattern.CASE_INSENSITIVE
    );

    // 日期时间组合查询
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
        "^(现在|当前|目前)?\\s*(日期时间|时间日期|几点几号)$" +
        "|^(what|what's|whats)?\\s*(is)?\\s*(the)?\\s*(current)?\\s*(date\\s*and\\s*time|datetime)\\s*(now)?$",
        Pattern.CASE_INSENSITIVE
    );

    // 带疑问词的时间查询
    private static final Pattern TIME_QUESTION_PATTERN = Pattern.compile(
        "^(现在|当前)?(是)?(几点|几时|什么时间|什么时刻)" +
        "|^(what|what's|whats)?\\s*time\\s*(is\\s*it)?$",
        Pattern.CASE_INSENSITIVE
    );

    // 带疑问词的日期查询
    private static final Pattern DATE_QUESTION_PATTERN = Pattern.compile(
        "^(今天|今日|当前)?(是)?(几号|什么日期|几月几号|哪年哪月哪日)" +
        "|^(what|what's|whats)?\\s*(the)?\\s*date\\s*(today)?$",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String getName() {
        return "TimeSkill";
    }

    @Override
    public String getDescription() {
        return "查询当前时间、日期等信息（支持：几点、时间、日期、几号等）";
    }

    @Override
    public boolean canHandle(String message) {
        String lowerMsg = message.toLowerCase(Locale.ROOT).trim();

        // 检查是否匹配时间/日期模式
        boolean canHandle = TIME_PATTERN.matcher(lowerMsg).matches() ||
                           DATE_PATTERN.matcher(lowerMsg).matches() ||
                           DATETIME_PATTERN.matcher(lowerMsg).matches() ||
                           TIME_QUESTION_PATTERN.matcher(lowerMsg).matches() ||
                           DATE_QUESTION_PATTERN.matcher(lowerMsg).matches();

        logger.debug("[TimeSkill] canHandle 检查 - 消息: '{}', 结果: {}", message, canHandle);
        return canHandle;
    }

    @Override
    public SkillResult execute(String message, SkillContext context) {
        logger.debug("[TimeSkill] 开始执行，消息: '{}', sessionId: '{}'", message, context.getSessionId());

        String lowerMsg = message.toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        logger.debug("[TimeSkill] 当前时间: {}", now);

        // 根据查询类型返回不同格式
        if (lowerMsg.contains("几点") || lowerMsg.contains("几时") ||
            lowerMsg.contains("时间") || lowerMsg.contains("时刻") ||
            lowerMsg.contains("time")) {
            String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String result = "🕐 当前时间：" + timeStr;
            logger.debug("[TimeSkill] 返回时间: {}", result);
            return SkillResult.success(result);
        } else if (lowerMsg.contains("几号") || lowerMsg.contains("日期") ||
                   lowerMsg.contains("年月日") || lowerMsg.contains("date")) {
            String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            String weekDay = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINESE));
            String result = "📅 今天是：" + dateStr + " " + weekDay;
            logger.debug("[TimeSkill] 返回日期: {}", result);
            return SkillResult.success(result);
        } else {
            // 默认返回完整日期时间
            String datetimeStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
            String weekDay = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINESE));
            String result = "🕐📅 当前日期时间：" + datetimeStr + " " + weekDay;
            logger.debug("[TimeSkill] 返回日期时间: {}", result);
            return SkillResult.success(result);
        }
    }

    @Override
    public int getPriority() {
        return 10; // 较高优先级
    }
}
