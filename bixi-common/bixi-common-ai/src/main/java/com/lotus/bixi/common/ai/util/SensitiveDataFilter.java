package com.lotus.bixi.common.ai.util;

import java.util.regex.Pattern;

/**
 * 敏感信息过滤工具类
 *
 * @author bixi
 * @date 2025-01-01
 */
public final class SensitiveDataFilter {

    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("\\b\\d{15,19}\\b"),
            Pattern.compile("\\b\\d{17}[0-9Xx]\\b"),
            Pattern.compile("\\b1[3-9]\\d{9}\\b")
    };

    private static final String MASK = "***";

    private SensitiveDataFilter() {
    }

    public static String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return result;
    }
}
