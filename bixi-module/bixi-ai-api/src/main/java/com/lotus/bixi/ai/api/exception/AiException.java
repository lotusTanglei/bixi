package com.lotus.bixi.ai.api.exception;

import lombok.NoArgsConstructor;

/**
 * AI 模块异常类
 *
 * @author bixi
 * @date 2026-03-14
 */
@NoArgsConstructor
public class AiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiException(String message) {
        super(message);
    }

    public AiException(Throwable cause) {
        super(cause);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
