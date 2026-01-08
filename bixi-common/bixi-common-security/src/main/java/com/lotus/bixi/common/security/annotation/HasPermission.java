package com.lotus.bixi.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 判断是否有权限
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@pms.hasPermission('{value}'.split(','))")
public @interface HasPermission {

    /**
     * 权限字符串
     *
     * @return {@link String[] }
     */
    String[] value();

}
