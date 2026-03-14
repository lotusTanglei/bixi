package com.lotus.bixi.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表单权限注解
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@formPms.hasFormPermission(#formKey, #permType)")
public @interface HasFormPermission {

    /**
     * 表单标识
     *
     * @return {@link String }
     */
    String formKey();

    /**
     * 权限类型：view-查看，edit-编辑，readonly-只读，hidden-隐藏
     *
     * @return {@link String }
     */
    String permType() default "view";

}
