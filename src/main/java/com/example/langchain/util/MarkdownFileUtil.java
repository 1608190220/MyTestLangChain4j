package com.example.langchain.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Markdown 文件工具类
 * 用于将内容保存为 .md 文件
 */
public class MarkdownFileUtil {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownFileUtil.class);

    // 默认存储目录
    private static final String DEFAULT_MD_DIR = "markdown/";

    // 日期格式化
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将内容保存为 Markdown 文件
     *
     * @param content 文件内容
     * @return 生成的文件路径
     */
    public static String saveMarkdownFile(String content) {
        return saveMarkdownFile(content, null, null);
    }

    /**
     * 将内容保存为 Markdown 文件
     *
     * @param content  文件内容
     * @param fileName 文件名（不含扩展名），为 null 时自动生成
     * @return 生成的文件路径
     */
    public static String saveMarkdownFile(String content, String fileName) {
        return saveMarkdownFile(content, fileName, null);
    }

    /**
     * 将内容保存为 Markdown 文件
     *
     * @param content     文件内容
     * @param fileName    文件名（不含扩展名），为 null 时自动生成
     * @param title       文档标题，会添加到文件头部
     * @return 生成的文件相对路径
     */
    public static String saveMarkdownFile(String content, String fileName, String title) {
        try {
            // 生成文件名
            String actualFileName = (fileName != null && !fileName.trim().isEmpty())
                    ? fileName.trim()
                    : generateFileName();

            // 确保文件名以 .md 结尾
            if (!actualFileName.toLowerCase().endsWith(".md")) {
                actualFileName += ".md";
            }

            // 创建目录
            File dir = new File(DEFAULT_MD_DIR);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (created) {
                    logger.debug("创建 Markdown 目录: {}", dir.getAbsolutePath());
                }
            }

            // 构建完整文件路径
            File file = new File(dir, actualFileName);

            // 构建文件内容
            StringBuilder fileContent = new StringBuilder();

            // 添加标题
            if (title != null && !title.trim().isEmpty()) {
                fileContent.append("# ").append(title.trim()).append("\n\n");
            }

            // 添加生成时间
            fileContent.append("<!-- 生成时间: ")
                    .append(LocalDateTime.now().format(DATE_FORMATTER))
                    .append(" -->\n\n");

            // 添加正文内容
            fileContent.append(content);

            // 写入文件（使用 UTF-8 编码）
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(fileContent.toString());
                writer.flush();
            }

            logger.info("Markdown 文件已保存: {}, 大小: {} 字节",
                    file.getAbsolutePath(), file.length());

            return DEFAULT_MD_DIR + actualFileName;

        } catch (IOException e) {
            logger.error("保存 Markdown 文件失败", e);
            throw new RuntimeException("保存 Markdown 文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成带时间戳的文件名
     */
    private static String generateFileName() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "doc_" + timestamp + "_" + uuid;
    }

    /**
     * 读取 Markdown 文件内容
     *
     * @param filePath 文件路径（相对路径或绝对路径）
     * @return 文件内容
     */
    public static String readMarkdownFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                // 尝试在默认目录下查找
                file = new File(DEFAULT_MD_DIR + filePath);
            }

            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            java.nio.file.Path path = file.toPath();
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            logger.error("读取 Markdown 文件失败: {}", filePath, e);
            throw new RuntimeException("读取 Markdown 文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Markdown 存储目录的绝对路径
     */
    public static String getMarkdownDirectory() {
        File dir = new File(DEFAULT_MD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    /**
     * 检查文件是否存在
     *
     * @param fileName 文件名
     * @return 是否存在
     */
    public static boolean exists(String fileName) {
        File file = new File(DEFAULT_MD_DIR + fileName);
        return file.exists();
    }
}
