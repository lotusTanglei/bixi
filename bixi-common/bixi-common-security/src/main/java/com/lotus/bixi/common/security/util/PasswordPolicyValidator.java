package com.lotus.bixi.common.security.util;

/**
 * 密码强度校验工具类
 *
 * @author 唐磊
 */
public final class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicyValidator() {
    }

    /**
     * 校验密码强度：至少8位，包含大写字母、小写字母、数字和特殊字符
     *
     * @param password 明文密码
     * @return true 表示满足强度要求
     */
    public static boolean isStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

}
