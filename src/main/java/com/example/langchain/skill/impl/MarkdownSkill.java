package com.example.langchain.skill.impl;

import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillResult;
import com.example.langchain.util.MarkdownFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文件生成 Skill
 * 支持用户通过对话生成 Markdown 文件
 *
 * 触发示例：
 * - "生成一个 Markdown 文件，内容是关于 Java 的介绍"
 * - "创建 md 文件：标题是 Spring Boot 教程"
 * - "保存为 markdown：# 标题\n内容..."
 */
@Component
public class MarkdownSkill implements Skill {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownSkill.class);

    // 匹配生成 Markdown 文件的各种表达
    private static final Pattern[] PATTERNS = {
            // 匹配 "生成/创建 markdown/md 文件"
            Pattern.compile("(?:生成|创建|保存|导出).{0,5}(?:markdown|md).{0,5}(?:文件|文档)?", Pattern.CASE_INSENSITIVE),
            // 匹配 "保存为 markdown/md"
            Pattern.compile("保存为.{0,3}(?:markdown|md)", Pattern.CASE_INSENSITIVE),
            // 匹配 "导出为 markdown/md"
            Pattern.compile("导出为.{0,3}(?:markdown|md)", Pattern.CASE_INSENSITIVE),
            // 匹配 "转成 markdown/md"
            Pattern.compile("转成.{0,3}(?:markdown|md)", Pattern.CASE_INSENSITIVE)
    };

    // 提取标题的正则
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?:标题[是为]|题目[是为]?|主题[是为]?|关于)[:：]?\\s*([^\\n,，.。;；]+)",
            Pattern.CASE_INSENSITIVE
    );

    // 提取文件名的正则
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "(?:文件名|名称|叫|命名为)[:：]?\\s*([^\\n,，.。;；\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean canHandle(String message) {
        String lowerMsg = message.toLowerCase();
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(lowerMsg).find()) {
                logger.debug("MarkdownSkill 匹配成功: {}", message);
                return true;
            }
        }
        return false;
    }

    @Override
    public SkillResult execute(String message, SkillContext context) {
        logger.info("执行 MarkdownSkill: {}", message);

        try {
            // 提取标题
            String title = extractTitle(message);

            // 提取文件名
            String fileName = extractFileName(message);

            // 提取内容（移除触发关键词）
            String content = extractContent(message);

            // 如果内容为空，返回提示
            if (content == null || content.trim().isEmpty()) {
                return SkillResult.success(
                        "📄 我可以帮你生成 Markdown 文件！\n\n" +
                                "请告诉我文件内容，例如：\n" +
                                "• \"生成 Markdown 文件，标题是 Java 入门教程，内容包括基础语法...\"\n" +
                                "• \"保存为 markdown：# Spring Boot 指南\\n\\n这是教程内容...\"\n" +
                                "• \"创建 md 文件，文件名是 notes，内容是会议纪要...\""
                );
            }

            // 保存文件
            String filePath = MarkdownFileUtil.saveMarkdownFile(content, fileName, title);

            // 获取文件名称
            String actualFileName = filePath.substring(filePath.lastIndexOf("/") + 1);

            logger.info("Markdown 文件生成成功: {}", filePath);

            // 构建成功响应
            StringBuilder result = new StringBuilder();
            result.append("✅ Markdown 文件已生成！\n\n");

            if (title != null && !title.isEmpty()) {
                result.append("📋 标题: ").append(title).append("\n");
            }

            result.append("📄 文件名: ").append(actualFileName).append("\n");
            result.append("📁 存储路径: ").append(filePath).append("\n\n");
            result.append("💡 提示：文件已保存在项目目录的 markdown/ 文件夹中");

            return SkillResult.success(result.toString());

        } catch (Exception e) {
            logger.error("生成 Markdown 文件失败", e);
            return SkillResult.failure("生成 Markdown 文件失败: " + e.getMessage());
        }
    }

    /**
     * 从消息中提取标题
     */
    private String extractTitle(String message) {
        Matcher matcher = TITLE_PATTERN.matcher(message);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            // 清理标题
            title = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            return title;
        }
        return null;
    }

    /**
     * 从消息中提取文件名
     */
    private String extractFileName(String message) {
        Matcher matcher = FILENAME_PATTERN.matcher(message);
        if (matcher.find()) {
            String fileName = matcher.group(1).trim();
            // 移除扩展名（如果有）
            fileName = fileName.replaceAll("\\.md$", "").replaceAll("\\.markdown$", "");
            // 清理文件名中的非法字符
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            return fileName;
        }
        return null;
    }

    /**
     * 从消息中提取内容
     * 移除触发关键词，保留实际内容
     */
    private String extractContent(String message) {
        String content = message;

        // 移除各种触发前缀
        String[] prefixes = {
                "生成.*?(?:markdown|md).*?(?:文件|文档)?[:：]?\\s*",
                "创建.*?(?:markdown|md).*?(?:文件|文档)?[:：]?\\s*",
                "保存为.*?(?:markdown|md)[:：]?\\s*",
                "导出为.*?(?:markdown|md)[:：]?\\s*",
                "转成.*?(?:markdown|md)[:：]?\\s*",
                "(?:标题|题目|主题|文件名|名称|叫|命名为)[:：]?[^\\n]+\\n?"
        };

        for (String prefix : prefixes) {
            content = content.replaceAll(prefix, "");
        }

        // 清理首尾空白
        content = content.trim();

        // 如果内容以 "内容是" 开头，移除它
        content = content.replaceFirst("^(?:内容[是为]|内容[:：])\\s*", "");

        return content;
    }

    @Override
    public String getName() {
        return "Markdown文件生成";
    }

    @Override
    public String getDescription() {
        return "将对话内容保存为 Markdown 文件，存储在项目目录下";
    }

    @Override
    public int getPriority() {
        return 80;
    }
}
