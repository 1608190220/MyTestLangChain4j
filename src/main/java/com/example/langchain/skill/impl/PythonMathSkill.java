package com.example.langchain.skill.impl;

import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillResult;
import com.example.langchain.util.PythonExecutor;
import com.example.langchain.util.PythonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Python 数学计算 Skill - 演示如何在 Skill 中调用 Python 脚本
 * 使用 Python 进行复杂数学运算
 */
@Component
public class PythonMathSkill implements Skill {

    private static final Logger logger = LoggerFactory.getLogger(PythonMathSkill.class);

    private PythonExecutor pythonExecutor;

    // Python 脚本存放目录
    private String scriptDir = "scripts/";

    @PostConstruct
    public void init() {
        pythonExecutor = new PythonExecutor();
        // 确保脚本目录存在
        File dir = new File(scriptDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建示例 Python 脚本
        createMathScript();
    }

    @Override
    public String getName() {
        return "PythonMathSkill";
    }

    @Override
    public String getDescription() {
        return "使用 Python 进行复杂数学计算（如阶乘、斐波那契、幂运算等）";
    }

    @Override
    public boolean canHandle(String message) {
        String lowerMsg = message.toLowerCase(Locale.ROOT);

        // 检查是否需要复杂数学计算
        boolean canHandle = lowerMsg.contains("阶乘") ||
                lowerMsg.contains("fibonacci") ||
                lowerMsg.contains("斐波那契") ||
                lowerMsg.contains("幂运算") ||
                lowerMsg.contains("平方根") ||
                lowerMsg.contains("开方") ||
                lowerMsg.contains("log") ||
                lowerMsg.contains("对数") ||
                lowerMsg.contains("sin") ||
                lowerMsg.contains("cos") ||
                lowerMsg.contains("三角函数") ||
                lowerMsg.contains("python计算") ||
                lowerMsg.contains("用python");

        logger.debug("[PythonMathSkill] canHandle 检查 - 消息: '{}', 结果: {}", message, canHandle);
        return canHandle;
    }

    @Override
    public SkillResult execute(String message, SkillContext context) {
        logger.debug("[PythonMathSkill] 开始执行，消息: '{}', sessionId: '{}'", message, context.getSessionId());

        String lowerMsg = message.toLowerCase(Locale.ROOT);

        try {
            // 提取数字
            int number = extractNumber(message);

            if (lowerMsg.contains("阶乘")) {
                return calculateFactorial(number);
            } else if (lowerMsg.contains("斐波那契") || lowerMsg.contains("fibonacci")) {
                return calculateFibonacci(number);
            } else if (lowerMsg.contains("平方根") || lowerMsg.contains("开方")) {
                return calculateSqrt(number);
            } else if (lowerMsg.contains("幂") || lowerMsg.contains("次方")) {
                // 尝试提取第二个数字作为幂指数
                int power = extractSecondNumber(message, 2);
                return calculatePower(number, power);
            } else {
                // 直接执行 Python 代码示例
                return executePythonCode(message);
            }
        } catch (Exception e) {
            logger.error("[PythonMathSkill] 执行失败", e);
            return SkillResult.failure("Python 计算失败: " + e.getMessage());
        }
    }

    @Override
    public int getPriority() {
        return 25; // 比 CalculatorSkill 更高优先级
    }

    /**
     * 计算阶乘
     */
    private SkillResult calculateFactorial(int n) {
        if (n < 0 || n > 20) {
            return SkillResult.failure("阶乘只支持 0-20 的整数");
        }

        String code = String.format(
            "import math; print(math.factorial(%d))", n
        );

        PythonResult result = pythonExecutor.executeCode(code);
        if (result.isSuccess()) {
            return SkillResult.success(String.format("%d 的阶乘 = %s", n, result.getOutput()));
        } else {
            return SkillResult.failure("阶乘计算失败: " + result.getError());
        }
    }

    /**
     * 计算斐波那契数列
     */
    private SkillResult calculateFibonacci(int n) {
        if (n < 1 || n > 100) {
            return SkillResult.failure("斐波那契数列只支持 1-100 的整数");
        }

        String code = String.format(
            "def fib(n):\n" +
            "    if n <= 1:\n" +
            "        return n\n" +
            "    a, b = 0, 1\n" +
            "    for _ in range(2, n + 1):\n" +
            "        a, b = b, a + b\n" +
            "    return b\n" +
            "print(fib(%d))", n
        );

        PythonResult result = pythonExecutor.executeCode(code);
        if (result.isSuccess()) {
            return SkillResult.success(String.format("斐波那契数列第 %d 项 = %s", n, result.getOutput()));
        } else {
            return SkillResult.failure("斐波那契计算失败: " + result.getError());
        }
    }

    /**
     * 计算平方根
     */
    private SkillResult calculateSqrt(double n) {
        if (n < 0) {
            return SkillResult.failure("不能计算负数的平方根");
        }

        String code = String.format(
            "import math; print('%.6f' % math.sqrt(%f))", n
        );

        PythonResult result = pythonExecutor.executeCode(code);
        if (result.isSuccess()) {
            return SkillResult.success(String.format("√%.2f = %s", n, result.getOutput()));
        } else {
            return SkillResult.failure("平方根计算失败: " + result.getError());
        }
    }

    /**
     * 计算幂运算
     */
    private SkillResult calculatePower(double base, int exp) {
        String code = String.format(
            "print(%f ** %d)", base, exp
        );

        PythonResult result = pythonExecutor.executeCode(code);
        if (result.isSuccess()) {
            return SkillResult.success(String.format("%.2f 的 %d 次方 = %s", base, exp, result.getOutput()));
        } else {
            return SkillResult.failure("幂运算失败: " + result.getError());
        }
    }

    /**
     * 直接执行 Python 代码
     */
    private SkillResult executePythonCode(String message) {
        // 简单的示例：计算表达式
        String code = String.format(
            "try:\n" +
            "    result = eval('%s')\n" +
            "    print(result)\n" +
            "except:\n" +
            "    print('无法计算')",
            message.replace("'", "\\'")
        );

        PythonResult result = pythonExecutor.executeCode(code);
        if (result.isSuccess() && !"无法计算".equals(result.getOutput())) {
            return SkillResult.success("Python 计算结果: " + result.getOutput());
        } else {
            return SkillResult.passToAI();
        }
    }

    /**
     * 创建 Python 数学脚本文件
     */
    private void createMathScript() {
        String scriptContent =
            "# -*- coding: utf-8 -*-\n" +
            "\"\"\"\n" +
            "数学计算工具脚本\n" +
            "用于复杂数学运算\n" +
            "\"\"\"\n" +
            "\n" +
            "import math\n" +
            "\n" +
            "def factorial(n):\n" +
            "    \"\"\"计算阶乘\"\"\"\n" +
            "    return math.factorial(n)\n" +
            "\n" +
            "def fibonacci(n):\n" +
            "    \"\"\"计算斐波那契数列第 n 项\"\"\"\n" +
            "    if n <= 1:\n" +
            "        return n\n" +
            "    a, b = 0, 1\n" +
            "    for _ in range(2, n + 1):\n" +
            "        a, b = b, a + b\n" +
            "    return b\n" +
            "\n" +
            "def is_prime(n):\n" +
            "    \"\"\"判断是否为质数\"\"\"\n" +
            "    if n < 2:\n" +
            "        return False\n" +
            "    for i in range(2, int(math.sqrt(n)) + 1):\n" +
            "        if n % i == 0:\n" +
            "            return False\n" +
            "    return True\n" +
            "\n" +
            "if __name__ == '__main__':\n" +
            "    print('Math utility script loaded')\n";

        try {
            File scriptFile = new File(scriptDir + "math_utils.py");
            try (FileWriter writer = new FileWriter(scriptFile)) {
                writer.write(scriptContent);
            }
            logger.debug("[PythonMathSkill] 创建 Python 脚本: {}", scriptFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("[PythonMathSkill] 创建脚本失败", e);
        }
    }

    /**
     * 从消息中提取数字
     */
    private int extractNumber(String message) {
        // 简单提取第一个数字
        String[] parts = message.split("\\D+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
        return 0;
    }

    /**
     * 提取第二个数字
     */
    private int extractSecondNumber(String message, int defaultValue) {
        String[] parts = message.split("\\D+");
        int count = 0;
        for (String part : parts) {
            if (!part.isEmpty()) {
                count++;
                if (count == 2) {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                }
            }
        }
        return defaultValue;
    }
}
