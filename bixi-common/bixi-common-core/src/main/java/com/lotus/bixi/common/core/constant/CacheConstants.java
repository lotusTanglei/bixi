package com.lotus.bixi.common.core.constant;

import com.lotus.bixi.common.core.context.TenantContextHolder;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 缓存的key 常量
 */
public interface CacheConstants {

	/**
	 * Build a tenant-scoped cache namespace. Access tokens remain unscoped because
	 * the token value itself is globally unique.
	 */
	static String tenantKey(String prefix, Long tenantId) {
		Long effectiveTenantId = tenantId == null ? SecurityConstants.DEFAULT_TENANT_ID : tenantId;
		String normalized = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
		return normalized + ":TENANT:" + effectiveTenantId + ":";
	}

	/** Returns the current tenant namespace for Spring Cache SpEL expressions. */
	static String currentTenantKey(String prefix) {
		return tenantKey(prefix, TenantContextHolder.get());
	}

    /**
     * oauth 缓存前缀
     */
    String PROJECT_OAUTH_ACCESS = "token::access_token";

    /**
     * 验证码前缀
     */
    String DEFAULT_CODE_KEY = "DEFAULT_CODE_KEY:";

    /** SMS login challenges must never share keys with public image captchas. */
    String SMS_CODE_KEY = "SMS_CODE_KEY:";

    /**
     * 菜单信息缓存
     */
    String MENU_DETAILS = "menu_details";

    /**
     * 用户信息缓存
     */
    String USER_DETAILS = "user_details";

    /**
     * 字典信息缓存
     */
    String DICT_DETAILS = "dict_details";

    /**
     * 角色信息缓存
     */
    String ROLE_DETAILS = "role_details";

    /**
     * oauth 客户端信息
     */
    String CLIENT_DETAILS_KEY = "client:details";

    /**
     * 参数缓存
     */
    String PARAMS_DETAILS = "params_details";

    /**
     * 登录失败计数前缀
     */
    String LOGIN_FAIL_KEY = "LOGIN_FAIL_KEY:";

    /**
     * 短信发送频率限制前缀
     */
    String SMS_RATE_LIMIT_KEY = "SMS_RATE_LIMIT_KEY:";

    /**
     * 默认过期时间，单位：秒（12小时）
     */
    Long DEFAULT_EXPIRE_TIME = 60 * 60 * 12L;

}
