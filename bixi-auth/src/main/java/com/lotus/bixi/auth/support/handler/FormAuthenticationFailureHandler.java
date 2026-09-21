package com.lotus.bixi.auth.support.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 表单登录失败处理逻辑
 */
@Slf4j
public class FormAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /**
     * Called when an authentication attempt fails.
     *
     * @param request   the request during which the authentication attempt occurred.
     * @param response  the response.
     * @param exception the exception which was thrown to reject the authentication
     */
    @Override
    @SneakyThrows
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) {
        log.debug("表单登录失败:{}", exception.getLocalizedMessage());
        String error = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        // Keep Location relative: servlet sendRedirect may expand it to an internal proxy path.
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, "login?error=" + error);
    }

}
