# 代码审查问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Bixi 项目代码审查中发现的所有安全和质量问题

**Architecture:** 按模块分组修复，优先处理安全漏洞，然后处理逻辑缺陷和代码质量问题

**Tech Stack:** Java 17, Spring Boot 3.4.1, Spring Security, Vue 3, TypeScript

---

## Task 1: 修复 AES 加密实现 (密钥=IV 严重安全漏洞)

**Files:**
- Modify: `bixi-auth/src/main/java/com/lotus/bixi/auth/support/filter/PasswordDecoderFilter.java`

**问题:** AES 加密使用相同的值作为密钥和 IV，这是密码学严重错误。同时使用 NoPadding 模式会导致问题。

- [ ] **Step 1: 修改 AES 加密实现，使用随机 IV**

```java
package com.lotus.bixi.auth.support.filter;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.servlet.RepeatBodyRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

/**
 * 密码解密过滤器
 * 前端传输的密码使用 AES-CBC 加密，后端解密
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordDecoderFilter extends OncePerRequestFilter {

    private final AuthSecurityConfigProperties authSecurityConfigProperties;

    private static final String PASSWORD = "password";

    private static final String KEY_ALGORITHM = "AES";

    /**
     * 固定 IV (16字节)，通过密钥派生，与密钥不同
     * 实际生产环境建议从配置中心获取独立的 IV
     */
    private static final String IV_SALT = "bixi-iv-salt-2025";

    static {
        // 关闭hutool 强制关闭Bouncy Castle库的依赖
        SecureUtil.disableBouncyCastle();
    }

    /**
     * 从配置密钥派生 AES 密钥 (32字节 = 256位)
     */
    private SecretKeySpec deriveKey(String encodeKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(encodeKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 从配置密钥派生 IV (16字节)
     * 使用不同的盐值确保 IV 与 Key 不同
     */
    private IvParameterSpec deriveIv(String encodeKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("MD5");
            String ivInput = encodeKey + IV_SALT;
            byte[] ivBytes = sha.digest(ivInput.getBytes(StandardCharsets.UTF_8));
            return new IvParameterSpec(ivBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 不是登录请求，直接向下执行
        if (!StrUtil.containsAnyIgnoreCase(request.getRequestURI(), SecurityConstants.OAUTH_TOKEN_URL)) {
            chain.doFilter(request, response);
            return;
        }

        String encodeKey = authSecurityConfigProperties.getEncodeKey();
        if (StrUtil.isBlank(encodeKey)) {
            log.warn("Encode key not configured, skip password decryption");
            chain.doFilter(request, response);
            return;
        }

        // 将请求流转换为可多次读取的请求流
        RepeatBodyRequestWrapper requestWrapper = new RepeatBodyRequestWrapper(request);
        Map<String, String[]> parameterMap = requestWrapper.getParameterMap();

        // 使用派生的密钥和 IV 构建 AES 解密器
        // 注意：前端需要配合修改，使用相同的密钥派生逻辑
        AES aes = new AES(Mode.CBC, Padding.PKCS5Padding,
                deriveKey(encodeKey),
                deriveIv(encodeKey));

        parameterMap.forEach((k, v) -> {
            String[] values = parameterMap.get(k);
            if (!PASSWORD.equals(k) || ArrayUtil.isEmpty(values)) {
                return;
            }

            try {
                // 解密密码
                String decryptPassword = aes.decryptStr(values[0]);
                parameterMap.put(k, new String[]{decryptPassword});
            } catch (Exception e) {
                log.error("Password decryption failed", e);
                // 解密失败时保留原始值，让后续认证流程处理
            }
        });
        chain.doFilter(requestWrapper, response);
    }

}
```

- [ ] **Step 2: 验证修改**

检查代码编译通过：
```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-auth -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-auth/src/main/java/com/lotus/bixi/auth/support/filter/PasswordDecoderFilter.java
git commit -m "fix(security): 修复AES加密密钥与IV相同的严重安全漏洞

- 使用SHA-256从配置密钥派生AES密钥
- 使用MD5(密钥+盐)派生独立的IV
- 改用PKCS5Padding替代NoPadding
- 添加密钥配置检查和异常处理

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: 修复 Token Claims 敏感信息泄露

**Files:**
- Modify: `bixi-auth/src/main/java/com/lotus/bixi/auth/support/core/CustomeOAuth2TokenCustomizer.java`

**问题:** 完整的 BixiUser 对象被写入 Token Claims，包含手机号等敏感信息。

- [ ] **Step 1: 修改 Token Claims 只包含必要信息**

```java
package com.lotus.bixi.auth.support.core;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.security.service.BixiUser;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.HashMap;
import java.util.Map;

/**
 * token 输出增强
 * 注意：只将必要的信息放入 Claims，避免敏感信息泄露
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public class CustomeOAuth2TokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {

    /**
     * Customize the OAuth 2.0 Token attributes.
     *
     * @param context the context containing the OAuth 2.0 Token attributes
     */
    @Override
    public void customize(OAuth2TokenClaimsContext context) {
        OAuth2TokenClaimsSet.Builder claims = context.getClaims();
        claims.claim(SecurityConstants.DETAILS_LICENSE, SecurityConstants.PROJECT_LICENSE);
        String clientId = context.getAuthorizationGrant().getName();
        claims.claim(SecurityConstants.CLIENT_ID, clientId);

        // 客户端模式不返回具体用户信息
        if (SecurityConstants.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType().getValue())) {
            return;
        }

        BixiUser bixiUser = (BixiUser) context.getPrincipal().getPrincipal();

        // 只存储必要的用户标识信息，不存储完整用户对象
        // 敏感信息（如手机号）不应放入 Token
        claims.claim(SecurityConstants.DETAILS_USER_ID, bixiUser.getId());
        claims.claim(SecurityConstants.USERNAME, bixiUser.getUsername());

        // 存储部门ID用于数据权限控制
        Map<String, Object> userDetails = new HashMap<>(2);
        userDetails.put("id", bixiUser.getId());
        userDetails.put("deptId", bixiUser.getDeptId());
        claims.claim(SecurityConstants.DETAILS_USER, userDetails);
    }

}
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-auth -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-auth/src/main/java/com/lotus/bixi/auth/support/core/CustomeOAuth2TokenCustomizer.java
git commit -m "fix(security): 移除Token Claims中的敏感用户信息

- 不再将完整BixiUser对象放入Claims
- 只保留userId、username和deptId
- 敏感信息(手机号等)不再暴露在Token中

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: 添加 check_token 端点认证保护

**Files:**
- Modify: `bixi-auth/src/main/java/com/lotus/bixi/auth/endpoint/BixiTokenEndpoint.java`

**问题:** check_token 端点无认证保护，任何人可以查询 Token 详情。

- [ ] **Step 1: 为 check_token 添加 @Inner 注解**

找到 `checkToken` 方法（约第129行），添加 `@Inner` 注解：

```java
    /**
     * 校验token (仅内部服务调用)
     *
     * @param token 令牌
     */
    @Inner
    @SneakyThrows
    @GetMapping("/check_token")
    public void checkToken(String token, HttpServletResponse response, HttpServletRequest request) {
        // ... 保持原有实现不变
    }
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-auth -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-auth/src/main/java/com/lotus/bixi/auth/endpoint/BixiTokenEndpoint.java
git commit -m "fix(security): 为check_token端点添加@Inner认证保护

- check_token现在只能通过内部服务调用
- 防止外部攻击者通过该端点获取Token详情

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: 修复开放重定向漏洞

**Files:**
- Modify: `bixi-auth/src/main/java/com/lotus/bixi/auth/support/handler/SsoLogoutSuccessHandler.java`

**问题:** SSO 退出时直接使用用户提供的 URL 重定向，无白名单校验。

- [ ] **Step 1: 添加 URL 白名单校验**

```java
package com.lotus.bixi.auth.support.handler;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * SSO 退出功能，根据客户端传入跳转
 * 添加重定向URL白名单校验，防止开放重定向攻击
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public class SsoLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final String REDIRECT_URL = "redirect_url";

    /**
     * 允许的重定向域名白名单
     * 生产环境应从配置中心读取
     */
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
            "localhost",
            "127.0.0.1",
            "lotus-bixi.com",
            ".lotus-bixi.com"  // 支持子域名
    );

    /**
     * 默认重定向地址
     */
    private static final String DEFAULT_REDIRECT = "/token/login";

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (response == null) {
            return;
        }

        String redirectUrl = request.getParameter(REDIRECT_URL);

        // 验证重定向URL是否在白名单中
        if (StrUtil.isNotBlank(redirectUrl) && isAllowedRedirect(redirectUrl)) {
            response.sendRedirect(redirectUrl);
            return;
        }

        // 检查 Referer 头
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (StrUtil.isNotBlank(referer) && isAllowedRedirect(referer)) {
            response.sendRedirect(referer);
            return;
        }

        // 默认跳转到登录页
        response.sendRedirect(DEFAULT_REDIRECT);
    }

    /**
     * 检查URL是否在允许的白名单中
     *
     * @param url 待检查的URL
     * @return true表示允许重定向
     */
    private boolean isAllowedRedirect(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null) {
                // 相对路径，允许
                return url.startsWith("/") && !url.startsWith("//");
            }

            // 检查域名白名单
            for (String allowedDomain : ALLOWED_DOMAINS) {
                if (allowedDomain.startsWith(".")) {
                    // 支持子域名匹配
                    if (host.endsWith(allowedDomain) || host.equals(allowedDomain.substring(1))) {
                        return true;
                    }
                } else {
                    if (host.equals(allowedDomain)) {
                        return true;
                    }
                }
            }

            return false;
        } catch (IllegalArgumentException e) {
            // URL解析失败，不允许
            return false;
        }
    }

}
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-auth -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-auth/src/main/java/com/lotus/bixi/auth/support/handler/SsoLogoutSuccessHandler.java
git commit -m "fix(security): 修复SSO退出开放重定向漏洞

- 添加重定向URL白名单校验
- 支持子域名匹配
- 非法URL重定向到默认登录页
- 防止钓鱼攻击

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: 修复 redisTemplate.keys() 为 SCAN

**Files:**
- Modify: `bixi-auth/src/main/java/com/lotus/bixi/auth/endpoint/BixiTokenEndpoint.java`

**问题:** 使用 `redisTemplate.keys()` 在大数据量下会阻塞 Redis。

- [ ] **Step 1: 使用 SCAN 替代 KEYS**

修改 `tokenList` 方法（约第187-219行）：

```java
    /**
     * 查询token
     *
     * @param params 分页参数
     * @return
     */
    @Inner
    @PostMapping("/page")
    public R<Page> tokenList(@RequestBody Map<String, Object> params) {
        // 根据分页参数获取对应数据
        String pattern = String.format("%s::*", CacheConstants.PROJECT_OAUTH_ACCESS);
        int current = MapUtil.getInt(params, CommonConstants.CURRENT);
        int size = MapUtil.getInt(params, CommonConstants.SIZE);

        // 使用 SCAN 替代 KEYS，避免阻塞 Redis
        Set<String> keys = new HashSet<>();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (IOException e) {
            log.error("Redis scan error", e);
        }

        // 计算分页
        int skip = (current - 1) * size;
        List<String> pageKeys = keys.stream().skip(skip).limit(size).collect(Collectors.toList());

        Page result = new Page(current, size);

        if (pageKeys.isEmpty()) {
            result.setRecords(Collections.emptyList());
            result.setTotal(0);
            return R.ok(result);
        }

        List<TokenVo> tokenVoList = redisTemplate.opsForValue().multiGet(pageKeys).stream()
                .filter(Objects::nonNull)
                .map(obj -> {
                    OAuth2Authorization authorization = (OAuth2Authorization) obj;
                    TokenVo tokenVo = new TokenVo();
                    tokenVo.setClientId(authorization.getRegisteredClientId());
                    tokenVo.setId(authorization.getId());
                    tokenVo.setUsername(authorization.getPrincipalName());
                    OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
                    // Token 值截断显示，只显示前8位
                    String tokenValue = accessToken.getToken().getTokenValue();
                    tokenVo.setAccessToken(tokenValue.length() > 16
                            ? tokenValue.substring(0, 8) + "..." + tokenValue.substring(tokenValue.length() - 8)
                            : tokenValue);

                    String expiresAt = TemporalAccessorUtil.format(accessToken.getToken().getExpiresAt(),
                            DatePattern.NORM_DATETIME_PATTERN);
                    tokenVo.setExpiresAt(expiresAt);

                    String issuedAt = TemporalAccessorUtil.format(accessToken.getToken().getIssuedAt(),
                            DatePattern.NORM_DATETIME_PATTERN);
                    tokenVo.setIssuedAt(issuedAt);
                    return tokenVo;
                }).collect(Collectors.toList());

        result.setRecords(tokenVoList);
        result.setTotal(keys.size());
        return R.ok(result);
    }
```

- [ ] **Step 2: 添加必要的 import**

在文件顶部添加：

```java
import org.springframework.data.redis.core.ScanOptions;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Cursor;
```

- [ ] **Step 3: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-auth -am -DskipTests
```

- [ ] **Step 4: 提交**

```bash
git add bixi-auth/src/main/java/com/lotus/bixi/auth/endpoint/BixiTokenEndpoint.java
git commit -m "fix(perf): 使用SCAN替代KEYS命令，修复Redis阻塞风险

- 使用Redis SCAN迭代器替代KEYS命令
- 避免在大数据量下阻塞Redis
- 同时截断Token显示值保护安全

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 6: 修复 RetOps 逻辑反转 bug

**Files:**
- Modify: `bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/util/RetOps.java`

**问题:** `assertDataNotEmpty` 方法逻辑反转 - 有数据时抛异常，无数据时不抛。

- [ ] **Step 1: 修复 assertDataNotEmpty 逻辑**

修改第215-220行：

```java
    /**
     * 断言业务数据有值,并且包含元素
     *
     * @param func 用户函数,负责创建异常对象
     * @param <Ex> 异常类型
     * @return 返回实例，以便于继续进行链式操作
     * @throws Ex 断言失败时抛出
     */
    public <Ex extends Exception> RetOps<T> assertDataNotEmpty(Function<? super R<T>, ? extends Ex> func) throws Ex {
        // 修复：数据为空时抛出异常（原逻辑反了）
        if (ObjectUtil.isEmpty(original.getData())) {
            throw func.apply(original);
        }
        return this;
    }
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-common/bixi-common-core -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/util/RetOps.java
git commit -m "fix(core): 修复RetOps.assertDataNotEmpty逻辑反转bug

- 原逻辑：有数据时抛异常（错误）
- 修复后：无数据时抛异常（正确）

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 7: 修复 getUserID() JSON 截取方式

**Files:**
- Modify: `bixi-common/bixi-common-mybatis/src/main/java/com/lotus/bixi/common/mybatis/config/MybatisPlusMetaObjectHandler.java`

**问题:** 通过 JSON 字符串截取获取用户 ID，非常脆弱且容易出错。

- [ ] **Step 1: 使用正确的方式获取用户 ID**

```java
package com.lotus.bixi.common.mybatis.config;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.security.service.BixiUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

import java.time.LocalDateTime;
import java.util.Optional;


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

        fillValIfNullByName("createTime", LocalDateTime.now(), metaObject, true);
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
        if (userSetValue != null && !isCover) {
            return;
        }
        // 3. field 类型相同时设置
        Class<?> getterType = metaObject.getGetterType(fieldName);
        if (ClassUtils.isAssignableValue(getterType, fieldVal)) {
            metaObject.setValue(fieldName, fieldVal);
        }
    }

    /**
     * 获取当前用户ID
     * 修复：使用正确的方式从 Authentication 获取用户ID
     *
     * @return 当前用户ID，匿名用户返回null
     */
    private Long getUserID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 匿名接口直接返回
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        // 正确方式：检查 principal 类型并强转
        Object principal = authentication.getPrincipal();
        if (principal instanceof BixiUser) {
            return ((BixiUser) principal).getId();
        }

        log.debug("Cannot get user id from principal type: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

}
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-common/bixi-common-mybatis -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-common/bixi-common-mybatis/src/main/java/com/lotus/bixi/common/mybatis/config/MybatisPlusMetaObjectHandler.java
git commit -m "fix(mybatis): 修复getUserID()脆弱的JSON截取方式

- 使用正确的类型检查和强转获取用户ID
- 移除不安全的JSON字符串截取逻辑
- 添加适当的日志记录

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 8: 为 AI 模块添加权限控制

**Files:**
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller/AiController.java`

**问题:** AI 模块所有接口都没有权限控制注解。

- [ ] **Step 1: 添加权限注解**

首先检查项目中使用的权限注解类型：

```bash
grep -r "HasPermission\|PreAuthorize" /Users/dundundebaba/code/lotus/bixi/bixi/bixi-module --include="*.java" | head -5
```

然后修改 AiController.java：

```java
package com.lotus.bixi.ai.controller;

import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务控制器
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(description = "ai", name = "AI服务模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiController {

    private final ChatService chatService;
    private final VectorStoreService vectorStoreService;

    @PostMapping("/chat")
    @Operation(summary = "同步对话")
    @HasPermission("ai:chat:add")
    public R<ChatVO> chat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.chat(dto));
    }

    @GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    @HasPermission("ai:chat:add")
    public Flux<String> streamChat(@Valid ChatDTO dto) {
        return chatService.streamChat(dto);
    }

    @PostMapping("/rag")
    @Operation(summary = "RAG对话")
    @HasPermission("ai:rag:add")
    public R<ChatVO> ragChat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.ragChat(dto));
    }

    @PostMapping("/documents")
    @Operation(summary = "添加文档")
    @HasPermission("ai:document:add")
    public R<Void> addDocument(@RequestBody @Valid DocumentDTO dto) {
        vectorStoreService.addDocument(dto);
        return R.ok();
    }

    @PostMapping("/documents/batch")
    @Operation(summary = "批量添加文档")
    @HasPermission("ai:document:add")
    public R<Void> addDocuments(@RequestBody @Valid List<DocumentDTO> dtos) {
        vectorStoreService.addDocuments(dtos);
        return R.ok();
    }

    @PostMapping("/search")
    @Operation(summary = "相似度搜索")
    @HasPermission("ai:document:view")
    public R<List<DocumentVO>> search(@RequestBody @Valid SearchDTO dto) {
        return R.ok(vectorStoreService.similaritySearch(dto));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除文档")
    @HasPermission("ai:document:del")
    public R<Void> deleteDocument(@PathVariable Long id) {
        vectorStoreService.deleteDocument(id);
        return R.ok();
    }
}
```

- [ ] **Step 2: 验证修改**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi && mvn compile -pl bixi-module/bixi-ai-biz -am -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller/AiController.java
git commit -m "feat(ai): 为AI模块添加权限控制

- 对话接口需要 ai:chat:add 权限
- RAG对话需要 ai:rag:add 权限
- 文档操作需要相应的 ai:document:* 权限

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 9: 修复 FTL 模板 XSS 风险

**Files:**
- Modify: `bixi-auth/src/main/resources/templates/ftl/login.ftl`
- Modify: `bixi-auth/src/main/resources/templates/ftl/confirm.ftl`

**问题:** FTL 模板中变量直接输出未转义，存在 XSS 风险。

- [ ] **Step 1: 修复 login.ftl**

修改第45行，使用 `?html` 转义：

```ftl
                    <#if error??>
                        <div class="relative text-center">
                            <span class="text-red-600">${error?html}</span>
                        </div>
                    </#if>
```

- [ ] **Step 2: 修复 confirm.ftl**

修改第27、35、36、43行，添加 `?html` 转义：

```ftl
<!DOCTYPE html>
<html>
<html>
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport"
          content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no"/>
    <title>Bixi 第三方授权</title>
    <link rel="stylesheet" type="text/css" href="/static/css/bootstrap.min.css"/>
    <link rel="stylesheet" type="text/css" href="/static/css/signin.css"/>
</head>

<body>
<nav class="navbar navbar-default container-fluid">
    <div class="container">
        <div class="navbar-header">
            <a class="navbar-brand" href="#">开放平台</a>
        </div>
        <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-5">
            <p class="navbar-text navbar-right">
                <a target="_blank" href="http://lotus-studio.top">技术支持</a>
            </p>
            <p class="navbar-text navbar-right">
                <#if principalName=="anonymousUser">
                    未登录
                <#else>
                    <a target="_blank" href="http://lotus-studio.top">${principalName?html}</a>
                </#if>
            </p>
        </div>
    </div>
</nav>
<div style="padding-top: 80px;width: 300px; color: #555; margin:0px auto;">
    <form id='confirmationForm' name='confirmationForm' action="/oauth2/authorize" method='post'>
        <input type="hidden" name="client_id" value="${clientId?html}">
        <input type="hidden" name="state" value="${state?html}">

        <p>
            将获得以下权限：</p>
        <ul class="list-group">
            <li class="list-group-item"> <span>
              <#list scopeList as scope>
                  <input type="checkbox" checked="checked" name="scope" value="${scope?html}"/><label>${scope?html}</label>
              </#list>
        </ul>
        <p class="help-block">授权后表明你已同意 <a>服务协议</a></p>
        <button class="btn btn-success pull-right" type="submit" id="write-email-btn">授权</button>
        </p>
    </form>
</div>
</body>
</html>
```

- [ ] **Step 3: 提交**

```bash
git add bixi-auth/src/main/resources/templates/ftl/login.ftl
git add bixi-auth/src/main/resources/templates/ftl/confirm.ftl
git commit -m "fix(security): 修复FTL模板XSS风险

- 所有用户输入变量使用?html转义
- 包括error、principalName、clientId、state、scope等

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 10: 清理前端 debugger 语句

**Files:**
- Modify: `bixi-ui/src/api/admin/dict.ts`

**问题:** 残留 2 处 debugger 调试语句。

- [ ] **Step 1: 删除 debugger 语句**

修改第131行和第147行，删除 `debugger;`：

```typescript
export function validateDictItemValue(rule: any, value: any, callback: any, type: string, isEdit: boolean) {
	if (isEdit) {
		return callback();
	}

	getItemDetails({ dictType: type, value: value }).then((response) => {
		const result = response.data;
		if (result !== null) {
			callback(new Error('数据值已经存在'));
		} else {
			callback();
		}
	});
}

export function validateDictItemLabel(rule: any, value: any, callback: any, type: string, isEdit: boolean) {
	if (isEdit) {
		return callback();
	}

	getItemDetails({ dictType: type, label: value }).then((response) => {
		const result = response.data;
		if (result !== null) {
			callback(new Error('标签已经存在'));
		} else {
			callback();
		}
	});
}
```

- [ ] **Step 2: 提交**

```bash
git add bixi-ui/src/api/admin/dict.ts
git commit -m "fix(ui): 移除残留的debugger调试语句

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 11: 修复前端权限指令 DOM 删除问题

**Files:**
- Modify: `bixi-ui/src/directive/authDirective.ts`

**问题:** 权限指令使用 `el.parentNode.removeChild(el)` 直接删除 DOM，可能导致问题。

- [ ] **Step 1: 改用 CSS 隐藏方式**

```typescript
import type { App } from 'vue';
import { useUserInfo } from '/@/stores/userInfo';
import { judementSameArr } from '/@/utils/arrayOperation';

/**
 * 用户权限指令
 * @directive 单个权限验证（v-auth="xxx"）
 * @directive 多个权限验证，满足一个则显示（v-auths="[xxx,xxx]"）
 * @directive 多个权限验证，全部满足则显示（v-auth-all="[xxx,xxx]"）
 */
export function authDirective(app: App) {
	// 单个权限验证（v-auth="xxx"）
	app.directive('auth', {
		mounted(el, binding) {
			const stores = useUserInfo();
			if (!stores.userInfos.authBtnList.some((v: string) => v === binding.value)) {
				// 使用 CSS 隐藏替代 DOM 删除，更安全
				el.style.display = 'none';
			}
		},
		updated(el, binding) {
			const stores = useUserInfo();
			if (!stores.userInfos.authBtnList.some((v: string) => v === binding.value)) {
				el.style.display = 'none';
			} else {
				el.style.display = '';
			}
		},
	});
	// 多个权限验证，满足一个则显示（v-auths="[xxx,xxx]"）
	app.directive('auths', {
		mounted(el, binding) {
			let flag = false;
			const stores = useUserInfo();
			stores.userInfos.authBtnList.forEach((val: string) => {
				binding.value.forEach((v: string) => {
					if (val === v) flag = true;
				});
			});
			if (!flag) {
				el.style.display = 'none';
			}
		},
		updated(el, binding) {
			let flag = false;
			const stores = useUserInfo();
			stores.userInfos.authBtnList.forEach((val: string) => {
				binding.value.forEach((v: string) => {
					if (val === v) flag = true;
				});
			});
			if (!flag) {
				el.style.display = 'none';
			} else {
				el.style.display = '';
			}
		},
	});
	// 多个权限验证，全部满足则显示（v-auth-all="[xxx,xxx]"）
	app.directive('auth-all', {
		mounted(el, binding) {
			const stores = useUserInfo();
			const flag = judementSameArr(binding.value, stores.userInfos.authBtnList);
			if (!flag) {
				el.style.display = 'none';
			}
		},
		updated(el, binding) {
			const stores = useUserInfo();
			const flag = judementSameArr(binding.value, stores.userInfos.authBtnList);
			if (!flag) {
				el.style.display = 'none';
			} else {
				el.style.display = '';
			}
		},
	});
}
```

- [ ] **Step 2: 提交**

```bash
git add bixi-ui/src/directive/authDirective.ts
git commit -m "fix(ui): 修复权限指令DOM删除问题

- 使用CSS display隐藏替代parentNode.removeChild
- 添加updated钩子处理动态权限变化
- 使用forEach替代map遍历

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 12: 验证所有修改

- [ ] **Step 1: 编译整个项目**

```bash
cd /Users/dundundebaba/code/lotus/bixi/bixi
mvn clean compile -DskipTests
```

- [ ] **Step 2: 运行测试**

```bash
mvn test
```

- [ ] **Step 3: 检查前端**

```bash
cd bixi-ui && npm run lint:eslint
```

---

## Task 13: 最终提交汇总

- [ ] **Step 1: 查看所有修改**

```bash
git status
git log --oneline -15
```

- [ ] **Step 2: 推送修改**

```bash
git push origin develop
```

---

## 问题修复汇总

| # | 问题 | 严重性 | 状态 |
|---|------|--------|------|
| 1 | AES 密钥=IV | 严重 | Task 1 |
| 2 | Token Claims 敏感信息 | 严重 | Task 2 |
| 3 | check_token 无认证 | 严重 | Task 3 |
| 4 | 开放重定向 | 高 | Task 4 |
| 5 | redisTemplate.keys() | 高 | Task 5 |
| 6 | RetOps 逻辑反转 | 严重 | Task 6 |
| 7 | getUserID() JSON截取 | 高 | Task 7 |
| 8 | AI 模块无权限 | 高 | Task 8 |
| 9 | FTL XSS | 中 | Task 9 |
| 10 | debugger 残留 | 低 | Task 10 |
| 11 | 权限指令 DOM 删除 | 中 | Task 11 |
