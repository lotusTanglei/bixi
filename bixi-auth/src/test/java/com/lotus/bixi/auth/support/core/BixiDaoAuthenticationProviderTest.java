package com.lotus.bixi.auth.support.core;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BixiDaoAuthenticationProviderTest {

    private StaticApplicationContext context;
    private MockHttpServletRequest request;
    private BixiDaoAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("securityMessageSource", new StaticMessageSource());
        context.getBeanFactory().registerSingleton("users", (BixiUserDetailsService) username ->
                User.withUsername(username).password("{noop}correct-password").authorities("demo_task_view").build());
        context.refresh();
        var springUtil = new SpringUtil();
        springUtil.setApplicationContext(context);
        springUtil.postProcessBeanFactory(context.getBeanFactory());
        request = new MockHttpServletRequest("POST", "/token/form");
        request.setParameter("client_id", "bixi");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        provider = new BixiDaoAuthenticationProvider();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        context.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"password", "mobile", "client_credentials", "refresh_token"})
    void requestGrantTypeCannotDisablePasswordVerification(String grantType) {
        if (grantType != null) {
            request.setParameter("grant_type", grantType);
        }
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void requestGrantCannotSelectSmsUserDirectoryForPasswordToken() {
        context.getBeanFactory().registerSingleton("smsUsers", new BixiUserDetailsService() {
            @Override public boolean support(String clientId, String grant) { return "mobile".equals(grant); }
            @Override public int getOrder() { return 100; }
            @Override public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String name) {
                return User.withUsername("sms-directory-user").password("{noop}correct-password").authorities("user").build();
            }
        });
        request.setParameter("grant_type", "mobile");
        var authenticated = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
        assertThat(authenticated.getName()).isEqualTo("alice");
    }

    @Test
    void missingPasswordCannotAuthenticateAFormUser() {
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void formLoginWithCorrectPasswordDoesNotRequireClientCredentials() {
        request.removeParameter("client_id");
        assertThatCode(() -> {
            var authenticated = provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
            assertThat(authenticated.isAuthenticated()).isTrue();
            assertThat(authenticated.getName()).isEqualTo("alice");
        }).doesNotThrowAnyException();
    }

    @Test
    void passwordGrantStillAuthenticatesCorrectCredentials() {
        request.setParameter("grant_type", "password");
        var authenticated = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
        assertThat(authenticated.isAuthenticated()).isTrue();
        assertThat(authenticated.getName()).isEqualTo("alice");
    }
}
