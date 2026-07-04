package com.lotus.bixi.common.mybatis.config;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lotus.bixi.common.core.constant.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


/**
 * MybatisPlus 自动填充配置
 *
 * @author 唐磊
 */
@Slf4j
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("mybatis plus start insert fill ....");

        fillValIfNullByName("createTime",  LocalDateTime.now(), metaObject, true);
        fillValIfNullByName("createBy", getUserID(), metaObject, true);

        // 删除标记自动填充
        fillValIfNullByName("delFlag", CommonConstants.STATUS_NORMAL, metaObject, true);
        fillValIfNullByName("status", CommonConstants.STATUS_NORMAL, metaObject, true);
        fillValIfNullByName("dataStatus", CommonConstants.STATUS_NORMAL, metaObject, true);

    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("mybatis plus start update fill ....");
        fillValIfNullByName("updateTime", LocalDateTime.now(), metaObject, true);
        fillValIfNullByName("updateBy", getUserID(), metaObject, true);
    }

    /**
     * 填充值，先判断是否有手动设置，优先手动设置的值，例如：job必须手动设置
     *
     * @param fieldName  属性名
     * @param fieldVal   属性值
     * @param metaObject MetaObject
     * @param isCover    是否覆盖原有值,避免更新操作手动入参
     */
    private static void fillValIfNullByName(String fieldName, Object fieldVal, MetaObject metaObject, boolean isCover) {
        // 0. 如果填充值为空
        if (fieldVal == null) {
            return;
        }

        // 1. 没有 set 方法
        if (!metaObject.hasSetter(fieldName)) {
            return;
        }
        // 2. 如果用户有手动设置的值
        Object userSetValue = metaObject.getValue(fieldName);
        String setValueStr = StrUtil.str(userSetValue, Charset.defaultCharset());
        if (StrUtil.isNotBlank(setValueStr) && !isCover) {
            return;
        }
        // 3. field 类型相同时设置
        Class<?> getterType = metaObject.getGetterType(fieldName);
        if (ClassUtils.isAssignableValue(getterType, fieldVal)) {
            metaObject.setValue(fieldName, fieldVal);
        }
    }

    /**
     * 获取 spring security 当前的用户ID
     *
     * @return 当前用户ID
     */
    private Long getUserID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 匿名接口直接返回
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        try {
            Object id = principal == null ? null : principal.getClass().getMethod("getId").invoke(principal);
            if (id instanceof Long) {
                return (Long) id;
            }
        }
        catch (ReflectiveOperationException ignored) {
            log.debug("Cannot read user id from principal type: {}",
                    principal != null ? principal.getClass().getName() : "null");
        }

        log.debug("Cannot get user id from principal type: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

}
