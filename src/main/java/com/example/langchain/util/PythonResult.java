package com.example.langchain.util;

/**
 * Python 执行结果封装
 */
public class PythonResult {

    private final boolean success;
    private final String output;
    private final String error;

    private PythonResult(boolean success, String output, String error) {
        this.success = success;
        this.output = output;
        this.error = error;
    }

    public static PythonResult success(String output) {
        return new PythonResult(true, output, null);
    }

    public static PythonResult failure(String error, String output) {
        return new PythonResult(false, output, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    /**
     * 获取输出作为整数
     */
    public int getOutputAsInt() {
        try {
            return Integer.parseInt(output.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取输出作为双精度浮点数
     */
    public double getOutputAsDouble() {
        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public String toString() {
        return "PythonResult{" +
                "success=" + success +
                ", output='" + output + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
