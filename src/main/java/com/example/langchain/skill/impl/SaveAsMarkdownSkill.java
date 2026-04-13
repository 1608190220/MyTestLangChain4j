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
 * 保存内容为 Markdown 文件 Skill
 * 
 * 功能：将消息内容（通常是AI的回复）保存为 Markdown 文件
 * 
 * 触发方式：
 * - "保存以上内容为md"
 * - "把上面内容存成markdown文件"
 * - "导出刚才的回复到md"
 * - "保存为markdown：标题xxx"
 * 
 * 特点：
 * - 可以保存当前对话内容
 * - 支持指定标题和文件名
 * - 自动清理触发指令，保留纯内容
 */
@Component
public class SaveAsMarkdownSkill implements Skill {

    private static final Logger logger = LoggerFactory.getLogger(SaveAsMarkdownSkill.class);

    // 匹配保存指令
    private static final Pattern[] SAVE_PATTERNS = {
            // 保存/导出/存成 以上/上面/刚才/当前 内容
            Pattern.compile("(?:保存|导出|存成|存储).{0,5}(?:以上|上面|刚才|当前|这个|此).{0,5}(?:内容|回复|回答|对话|消息)?.{0,3}(?:为|成|到)?.{0,3}(?:markdown|md)", Pattern.CASE_INSENSITIVE),
            // 把...保存/存成...
            Pattern.compile("把.{0,10}(?:内容|回复|回答).{0,5}(?:保存|存成|导出).{0,3}(?:为|成|到)?.{0,3}(?:markdown|md)", Pattern.CASE_INSENSITIVE),
            // 直接保存为
            Pattern.compile("(?:保存|存成|导出).{0,3}(?:为|成)?[:：]?\\s*(?:markdown|md)", Pattern.CASE_INSENSITIVE)
    };

    // 提取标题
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?:标题|题目|主题)[:：]?\\s*([^\\n,，.。;；]+)",
            Pattern.CASE_INSENSITIVE
    );

    // 提取文件名
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "(?:文件名|名称|叫|命名为)[:：]?\\s*([^\\n,，.。;；\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // 提取内容（在"保存为"之后的内容）
    private static final Pattern CONTENT_PATTERN = Pattern.compile(
            "(?:保存|存成|导出).{0,10}(?:markdown|md).{0,10}(?:内容)?[:：]?\\s*([\\s\\S]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean canHandle(String message) {
        String lowerMsg = message.toLowerCase();
        for (Pattern pattern : SAVE_PATTERNS) {
            if (pattern.matcher(lowerMsg).find()) {
                logger.debug("SaveAsMarkdownSkill 匹配成功: {}", message);
                return true;
            }
        }
        return false;
    }

    @Override
    public SkillResult execute(String message, SkillContext context) {
        logger.info("执行 SaveAsMarkdownSkill: {}", message);

        try {
            // 提取标题
            String title = extractTitle(message);

            // 提取文件名
            String fileName = extractFileName(message);

            // 提取内容
            String content = extractContent(message);

            // 如果内容为空，尝试从 context 获取历史消息
            if (content == null || content.trim().isEmpty()) {
                // 这里可以扩展为获取上一轮AI的回复
                return SkillResult.success(
                        "💾 我可以帮你保存内容为 Markdown 文件！\n\n" +
                        "使用方式：\n" +
                        "1. 直接保存内容：\"保存为markdown：这里是要保存的内容\"\n" +
                        "2. 指定标题：\"保存为md，标题是会议纪要，内容是...\"\n" +
                        "3. 指定文件名：\"保存为markdown，文件名是notes，内容是...\""
                );
            }

            // 保存文件
            String filePath = MarkdownFileUtil.saveMarkdownFile(content, fileName, title);
            String actualFileName = filePath.substring(filePath.lastIndexOf("/") + 1);

            logger.info("Markdown 文件保存成功: {}", filePath);

            // 构建响应
            StringBuilder result = new StringBuilder();
            result.append("✅ 内容已保存为 Markdown 文件！\n\n");
            
            if (title != null && !title.isEmpty()) {
                result.append("📋 标题: ").append(title).append("\n");
            }
            
            result.append("📄 文件: ").append(actualFileName).append("\n");
            result.append("📁 路径: ").append(filePath).append("\n");
            result.append("📊 大小: ").append(content.length()).append(" 字符\n\n");
            result.append("💡 提示：文件保存在项目 markdown/ 目录下");

            return SkillResult.success(result.toString());

        } catch (Exception e) {
            logger.error("保存 Markdown 文件失败", e);
            return SkillResult.failure("保存失败: " + e.getMessage());
        }
    }

    /**
     * 提取标题
     */
    private String extractTitle(String message) {
        Matcher matcher = TITLE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        return null;
    }

    /**
     * 提取文件名
     */
    private String extractFileName(String message) {
        Matcher matcher = FILENAME_PATTERN.matcher(message);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            return name.replaceAll("\\.md$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        return null;
    }

    /**
     * 提取内容
     */
    private String extractContent(String message) {
        // 先尝试匹配"保存为markdown：内容"格式
        Matcher matcher = CONTENT_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 否则清理所有指令词，保留剩余内容
        String content = message;
        String[] toRemove = {
            "保存.*?(?:markdown|md).*?(?:文件)?[:：]?\\s*",
            "存成.*?(?:markdown|md).*?(?:文件)?[:：]?\\s*",
            "导出.*?(?:markdown|md).*?(?:文件)?[:：]?\\s*",
            "(?:以上|上面|刚才|当前|这个|此).*?(?:内容|回复|对话)?",
            "(?:标题|文件名|名称|叫|命名为)[:：]?[^\\n]+\\n?"
        };

        for (String pattern : toRemove) {
            content = content.replaceAll(pattern, "");
        }

        // 清理内容前缀词
        content = content.replaceFirst("^(?:内容|正文)[:：]?\\s*", "").trim();

        return content;
    }

    @Override
    public String getName() {
        return "保存为Markdown";
    }

    @Override
    public String getDescription() {
        return "将消息内容保存为 Markdown 文件到项目目录";
    }

    @Override
    public int getPriority() {
        return 85; // 优先级略高于普通MarkdownSkill
    }
}
