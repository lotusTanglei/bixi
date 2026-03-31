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
