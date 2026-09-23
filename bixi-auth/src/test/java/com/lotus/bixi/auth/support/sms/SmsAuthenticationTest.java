package com.lotus.bixi.auth.support.sms;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.auth.support.core.BixiDaoAuthenticationProvider;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import com.lotus.bixi.common.core.constant.CacheConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsAuthenticationTest {
    private StaticApplicationContext context;
    private BixiDaoAuthenticationProvider users;
    private OAuth2ResourceOwnerSmsAuthenticationProvider sms;
    private ValueOperations<String, Object> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("securityMessageSource", new StaticMessageSource());
        context.getBeanFactory().registerSingleton("users", (BixiUserDetailsService) name ->
                User.withUsername(name).password("{noop}password").authorities("user").build());
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        context.getBeanFactory().registerSingleton("redisTemplate", redis);
        context.refresh();
        var util = new SpringUtil();
        util.setApplicationContext(context);
        util.postProcessBeanFactory(context.getBeanFactory());
        users = new BixiDaoAuthenticationProvider();
        sms = new OAuth2ResourceOwnerSmsAuthenticationProvider(users::authenticate,
                mock(OAuth2AuthorizationService.class), mock(OAuth2TokenGenerator.class));
        var request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("client_id", "ignored-client");
        request.setParameter("grant_type", "mobile");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ignored-client", null, java.util.List.of()));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "wrong"})
    void missingOrWrongCodeCannotAuthenticate(String code) {
        when(values.getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000")).thenReturn("123456");
        var parameters = new java.util.HashMap<String, Object>();
        parameters.put("mobile", "13800138000");
        parameters.put("code", code);
        assertThatThrownBy(() -> users.authenticate(sms.buildToken(parameters)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void expiredCodeCannotAuthenticate() {
        assertThatThrownBy(() -> users.authenticate(sms.buildToken(
                Map.of("mobile", "13800138000", "code", "123456"))))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void correctSmsCodeIsConsumedAtomicallyAndCannotBeReplayed() {
        when(values.getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000")).thenReturn("123456", null);
        assertThat(users.authenticate(sms.buildToken(Map.of("mobile", "13800138000", "code", "123456")))
                .isAuthenticated()).isTrue();
        assertThatThrownBy(() -> users.authenticate(sms.buildToken(
                Map.of("mobile", "13800138000", "code", "123456"))))
                .isInstanceOf(BadCredentialsException.class);
        verify(values, times(2)).getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000");
        verify(values, never()).get(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mobile", "code", "grant_type"})
    void repeatedCredentialParametersAreRejected(String parameter) {
        var request = new MockHttpServletRequest();
        request.setParameter("grant_type", "mobile");
        request.setParameter("mobile", "13800138000");
        request.setParameter("code", "123456");
        request.addParameter(parameter, "other");
        assertThatThrownBy(() -> new OAuth2ResourceOwnerSmsAuthenticationConverter().convert(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void missingCodeIsRejectedBeforeBuildingToken() {
        var request = new MockHttpServletRequest();
        request.setParameter("grant_type", "mobile");
        request.setParameter("mobile", "13800138000");
        assertThatThrownBy(() -> new OAuth2ResourceOwnerSmsAuthenticationConverter().convert(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
