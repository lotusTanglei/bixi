package com.lotus.bixi.common.core.exception;

import lombok.NoArgsConstructor;

/**
 * 验证码异常类
 * @author bixi
 * @date 2025-01-01
 */
@NoArgsConstructor
public class ValidateCodeException extends RuntimeException {

    private static final long serialVersionUID = -7285211528095468156L;

    public ValidateCodeException(String msg) {
        super(msg);
    }

}
