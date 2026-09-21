package com.lotus.bixi.auth.support.filter;

import com.lotus.bixi.common.core.util.SpringContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.redis.core.*;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidateCodeFilterTest {
    private StaticApplicationContext context;
    private ValueOperations<String, Object> values;
    private ValidateCodeFilter filter;
    private MockHttpServletRequest request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        context = new StaticApplicationContext();
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        context.getBeanFactory().registerSingleton("redisTemplate", redis);
        var logProperties = new com.lotus.bixi.common.log.config.BixiLogProperties();
        logProperties.setExcludeFields(List.of("password", "code"));
        context.getBeanFactory().registerSingleton("logProperties", logProperties);
        context.refresh();
        var util = new cn.hutool.extra.spring.SpringUtil();
        util.setApplicationContext(context);
        util.postProcessBeanFactory(context.getBeanFactory());
        new SpringContextHolder().setApplicationContext(context);
        var properties = new AuthSecurityConfigProperties();
        properties.setIgnoreClients(List.of());
        filter = new ValidateCodeFilter(properties);
        request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setServletPath("/oauth2/token");
        request.setParameter("client_id", "client");
        request.setParameter("grant_type", "password");
        request.setParameter("randomStr", "challenge");
        request.setParameter("code", "123456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        SpringContextHolder.clearHolder();
        context.close();
    }

    @Test void imageCaptchaIsConsumedAtomically() {
        when(values.getAndDelete("DEFAULT_CODE_KEY:challenge")).thenReturn("123456", null);
        AtomicBoolean reached = new AtomicBoolean();
        assertThatCode(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true)))
                .doesNotThrowAnyException();
        assertThat(reached).isTrue();
        var replay = new MockHttpServletResponse();
        reached.set(false);
        assertThatCode(() -> filter.doFilter(request, replay, (req, res) -> reached.set(true))).doesNotThrowAnyException();
        assertThat(reached).isFalse();
        assertThat(replay.getStatus()).isEqualTo(401);
        verify(values, times(2)).getAndDelete("DEFAULT_CODE_KEY:challenge");
        verify(values, never()).get(any());
    }

    @Test void mobileParameterCannotChangeImageChallengeNamespace() {
        request.setParameter("mobile", "13800138000");
        when(values.getAndDelete("DEFAULT_CODE_KEY:challenge")).thenReturn("123456");
        AtomicBoolean reached = new AtomicBoolean();
        assertThatCode(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true)))
                .doesNotThrowAnyException();
        assertThat(reached).isTrue();
        verify(values).getAndDelete("DEFAULT_CODE_KEY:challenge");
    }

    @Test void smsCredentialsAreLeftForAuthenticationProvider() {
        request.setParameter("grant_type", "mobile");
        request.removeParameter("randomStr");
        request.removeParameter("code");
        AtomicBoolean reached = new AtomicBoolean();
        assertThatCode(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true)))
                .doesNotThrowAnyException();
        assertThat(reached).isTrue();
        verifyNoInteractions(values);
    }
}
