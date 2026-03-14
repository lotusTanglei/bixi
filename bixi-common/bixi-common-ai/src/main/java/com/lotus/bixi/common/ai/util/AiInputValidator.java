package com.lotus.bixi.common.ai.util;

/**
 * AI 输入验证工具类
 *
 * @author bixi
 * @date 2025-01-01
 */
public final class AiInputValidator {

    private static final int MAX_INPUT_LENGTH = 10000;

    private AiInputValidator() {
    }

    public static void validate(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("输入长度超过限制，最大允许 " + MAX_INPUT_LENGTH + " 个字符");
        }
    }

    public static void validateLength(String input, int maxLength) {
        if (input != null && input.length() > maxLength) {
            throw new IllegalArgumentException("输入长度超过限制，最大允许 " + maxLength + " 个字符");
        }
    }
}
