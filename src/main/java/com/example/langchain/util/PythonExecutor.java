package com.example.langchain.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Python 脚本执行器
 * 用于在 Java 中调用 Python 脚本
 */
public class PythonExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);

    // Python 解释器路径，可从配置读取
    private String pythonPath = "python";

    // 脚本超时时间（秒）
    private int timeoutSeconds = 30;

    public PythonExecutor() {
    }

    public PythonExecutor(String pythonPath) {
        this.pythonPath = pythonPath;
    }

    /**
     * 执行 Python 脚本文件
     *
     * @param scriptPath 脚本路径
     * @param args       参数列表
     * @return 执行结果
     */
    public PythonResult executeScript(String scriptPath, String... args) {
        logger.debug("[PythonExecutor] 执行脚本: {}, 参数: {}", scriptPath, args);

        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptPath);
        for (String arg : args) {
            command.add(arg);
        }

        return execute(command);
    }

    /**
     * 直接执行 Python 代码
     *
     * @param pythonCode Python 代码字符串
     * @return 执行结果
     */
    public PythonResult executeCode(String pythonCode) {
        logger.debug("[PythonExecutor] 执行代码: {}", pythonCode.substring(0, Math.min(50, pythonCode.length())));

        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add("-c");
        command.add(pythonCode);

        return execute(command);
    }

    /**
     * 执行 Python 函数（通过脚本调用）
     *
     * @param scriptPath 脚本路径
     * @param function   函数名
     * @param args       JSON 格式的参数
     * @return 执行结果
     */
    public PythonResult executeFunction(String scriptPath, String function, String args) {
        logger.debug("[PythonExecutor] 调用函数: {}.{}, 参数: {}", scriptPath, function, args);

        // 构建调用代码
        String code = String.format(
            "import sys; sys.path.insert(0, '.'); " +
            "import json; " +
            "from %s import %s; " +
            "args = json.loads('%s'); " +
            "result = %s(**args) if isinstance(args, dict) else %s(*args) if isinstance(args, list) else %s(args); " +
            "print(json.dumps(result, ensure_ascii=False))",
            getModuleName(scriptPath), function, args, function, function, function
        );

        return executeCode(code);
    }

    /**
     * 执行命令
     */
    private PythonResult execute(List<String> command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // 合并错误输出到标准输出
            process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程完成
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("[PythonExecutor] 执行超时");
                return PythonResult.failure("执行超时", output.toString());
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                logger.debug("[PythonExecutor] 执行成功: {}", result);
                return PythonResult.success(result);
            } else {
                logger.error("[PythonExecutor] 执行失败，退出码: {}, 输出: {}", exitCode, result);
                return PythonResult.failure("退出码: " + exitCode, result);
            }

        } catch (Exception e) {
            logger.error("[PythonExecutor] 执行异常", e);
            return PythonResult.failure(e.getMessage(), "");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 从脚本路径获取模块名
     */
    private String getModuleName(String scriptPath) {
        String fileName = scriptPath.substring(scriptPath.lastIndexOf('/') + 1)
                .substring(scriptPath.lastIndexOf('\\') + 1);
        if (fileName.endsWith(".py")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    // Getters and Setters
    public String getPythonPath() {
        return pythonPath;
    }

    public void setPythonPath(String pythonPath) {
        this.pythonPath = pythonPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
