package com.example.langchain.skill.impl;

import com.example.langchain.skill.Skill;
import com.example.langchain.skill.SkillContext;
import com.example.langchain.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 算术计算 Skill - 支持加减乘除运算
 * 可以处理数学表达式计算
 */
@Component
public class CalculatorSkill implements Skill {

    private static final Logger logger = LoggerFactory.getLogger(CalculatorSkill.class);

    // 匹配数学表达式的正则：数字 运算符 数字
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
            "(-?\\d+\\.?\\d*)\\s*([+\\-×xX*÷/])\\s*(-?\\d+\\.?\\d*)"
    );

    // 匹配中文数字表达式
    private static final Pattern CHINESE_EXPRESSION_PATTERN = Pattern.compile(
            "([一二三四五六七八九十百千万亿\\d]+)\\s*([加减乘除])\\s*([一二三四五六七八九十百千万亿\\d]+)"
    );

    @Override
    public String getName() {
        return "CalculatorSkill";
    }

    @Override
    public String getDescription() {
        return "执行算术运算（加减乘除），支持表达式如：1+2、3×4、10÷2";
    }

    @Override
    public boolean canHandle(String message) {
        String lowerMsg = message.toLowerCase(Locale.ROOT);

        // 检查是否包含数学表达式
        boolean hasExpression = EXPRESSION_PATTERN.matcher(message).find() ||
                CHINESE_EXPRESSION_PATTERN.matcher(message).find();

        // 检查是否包含计算关键词
        boolean hasKeywords = lowerMsg.contains("计算") ||
                lowerMsg.contains("等于") ||
                lowerMsg.contains("是多少") ||
                lowerMsg.contains("算一下") ||
                lowerMsg.contains("结果是") ||
                lowerMsg.contains("calculator") ||
                lowerMsg.contains("calculate");

        boolean canHandle = hasExpression || hasKeywords;
        logger.debug("[CalculatorSkill] canHandle 检查 - 消息: '{}', 结果: {}", message, canHandle);
        return canHandle;
    }

    @Override
    public SkillResult execute(String message, SkillContext context) {
        logger.debug("[CalculatorSkill] 开始执行，消息: '{}', sessionId: '{}'", message, context.getSessionId());

        // 先尝试匹配标准数学表达式
        Matcher matcher = EXPRESSION_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                double num1 = Double.parseDouble(matcher.group(1));
                String operator = matcher.group(2);
                double num2 = Double.parseDouble(matcher.group(3));

                logger.debug("[CalculatorSkill] 解析表达式: {} {} {}", num1, operator, num2);

                double result = calculate(num1, operator, num2);
                String resultStr = formatResult(num1, operator, num2, result);

                logger.debug("[CalculatorSkill] 计算结果: {}", resultStr);
                return SkillResult.success(resultStr);
            } catch (Exception e) {
                logger.error("[CalculatorSkill] 计算失败: {}", e.getMessage());
                return SkillResult.failure("计算出错：" + e.getMessage());
            }
        }

        // 尝试匹配中文表达式
        Matcher chineseMatcher = CHINESE_EXPRESSION_PATTERN.matcher(message);
        if (chineseMatcher.find()) {
            try {
                String num1Str = chineseMatcher.group(1);
                String operator = chineseMatcher.group(2);
                String num2Str = chineseMatcher.group(3);

                double num1 = parseChineseNumber(num1Str);
                double num2 = parseChineseNumber(num2Str);
                String opSymbol = convertChineseOperator(operator);

                logger.debug("[CalculatorSkill] 解析中文表达式: {} {} {}", num1, opSymbol, num2);

                double result = calculate(num1, opSymbol, num2);
                String resultStr = formatResult(num1, opSymbol, num2, result);

                logger.debug("[CalculatorSkill] 计算结果: {}", resultStr);
                return SkillResult.success(resultStr);
            } catch (Exception e) {
                logger.error("[CalculatorSkill] 计算失败: {}", e.getMessage());
                return SkillResult.failure("计算出错：" + e.getMessage());
            }
        }

        logger.debug("[CalculatorSkill] 无法识别表达式，传递给 AI 处理");
        return SkillResult.passToAI();
    }

    @Override
    public int getPriority() {
        return 20; // 比 TimeSkill 更高优先级
    }

    /**
     * 执行计算
     */
    private double calculate(double num1, String operator, double num2) {
        switch (operator) {
            case "+":
            case "加":
                return num1 + num2;
            case "-":
            case "减":
                return num1 - num2;
            case "*":
            case "×":
            case "x":
            case "X":
            case "乘":
                return num1 * num2;
            case "/":
            case "÷":
            case "除":
                if (num2 == 0) {
                    throw new ArithmeticException("除数不能为零");
                }
                return num1 / num2;
            default:
                throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
    }

    /**
     * 格式化结果
     */
    private String formatResult(double num1, String operator, double num2, double result) {
        String opDisplay = getOperatorDisplay(operator);

        // 如果是整数结果，不显示小数点
        if (result == (long) result) {
            return String.format("%.0f %s %.0f = %.0f", num1, opDisplay, num2, result);
        } else {
            return String.format("%.2f %s %.2f = %.2f", num1, opDisplay, num2, result);
        }
    }

    /**
     * 获取运算符的显示形式
     */
    private String getOperatorDisplay(String operator) {
        switch (operator) {
            case "+":
            case "加":
                return "+";
            case "-":
            case "减":
                return "-";
            case "*":
            case "×":
            case "x":
            case "X":
            case "乘":
                return "×";
            case "/":
            case "÷":
            case "除":
                return "÷";
            default:
                return operator;
        }
    }

    /**
     * 转换中文运算符为符号
     */
    private String convertChineseOperator(String chineseOp) {
        switch (chineseOp) {
            case "加":
                return "+";
            case "减":
                return "-";
            case "乘":
                return "*";
            case "除":
                return "/";
            default:
                return chineseOp;
        }
    }

    /**
     * 解析中文数字
     */
    private double parseChineseNumber(String chineseNum) {
        // 如果是阿拉伯数字，直接解析
        if (chineseNum.matches("\\d+")) {
            return Double.parseDouble(chineseNum);
        }

        // 中文数字映射
        int result = 0;
        int temp = 0;
        int section = 0;

        for (char c : chineseNum.toCharArray()) {
            int num = chineseCharToNumber(c);
            if (num >= 0 && num <= 9) {
                temp = num;
            } else {
                int unit = chineseUnitToNumber(c);
                if (unit == 10 || unit == 100 || unit == 1000) {
                    temp = (temp == 0 ? 1 : temp) * unit;
                    section += temp;
                    temp = 0;
                } else if (unit == 10000 || unit == 100000000) {
                    section = (section + temp) * unit;
                    result += section;
                    section = 0;
                    temp = 0;
                }
            }
        }

        return result + section + temp;
    }

    private int chineseCharToNumber(char c) {
        switch (c) {
            case '零': return 0;
            case '一': return 1;
            case '二':
            case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private int chineseUnitToNumber(char c) {
        switch (c) {
            case '十': return 10;
            case '百': return 100;
            case '千': return 1000;
            case '万': return 10000;
            case '亿': return 100000000;
            default: return 0;
        }
    }
}
