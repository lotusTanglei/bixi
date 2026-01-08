package com.lotus.bixi.common.core.exception;

import lombok.NoArgsConstructor;

/**
 * 自定义服务拒绝异常类
 * @author 唐磊
 * @date 2025-01-01
 */
@NoArgsConstructor
public class BixiDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BixiDeniedException(String message) {
        super(message);
    }

    public BixiDeniedException(Throwable cause) {
        super(cause);
    }

    public BixiDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

    public BixiDeniedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
