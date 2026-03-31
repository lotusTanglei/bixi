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
                // 相对路径，允许（但必须以单个/开头）
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
