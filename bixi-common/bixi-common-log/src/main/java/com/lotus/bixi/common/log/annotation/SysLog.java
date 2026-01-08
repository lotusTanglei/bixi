

package com.lotus.bixi.common.log.annotation;

import java.lang.annotation.*;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLog {

    /**
     * 描述
     *
     * @return {String}
     */
    String value() default "";

    /**
     * spel 表达式
     *
     * @return 日志描述
     */
    String expression() default "";

}
