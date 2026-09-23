package com.lotus.bixi.auth.support.core;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.auth.support.sms.SmsAuthenticationToken;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BixiDaoAuthenticationProviderTest {

	private StaticApplicationContext context;
	private MockHttpServletRequest request;
	private BixiDaoAuthenticationProvider provider;
	private RedisTemplate<String, Object> redisTemplate;
	private ValueOperations<String, Object> valueOperations;

	@BeforeEach
	void setUp() {
		context = new StaticApplicationContext();
		context.getBeanFactory().registerSingleton("securityMessageSource", new StaticMessageSource());
		context.getBeanFactory().registerSingleton("users", (BixiUserDetailsService) username -> User
				.withUsername(username).password("{noop}correct-password").authorities("demo_task_view").build());

		redisTemplate = mock(RedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		context.getBeanFactory().registerSingleton("redisTemplate", redisTemplate);

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
	@ValueSource(strings = { "password", "mobile", "client_credentials", "refresh_token" })
	void requestGrantTypeCannotDisablePasswordVerification(String grantType) {
		if (grantType != null) {
			request.setParameter("grant_type", grantType);
		}
		assertThatThrownBy(() -> provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong-password")))
						.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void requestGrantCannotSelectSmsUserDirectoryForPasswordToken() {
		context.getBeanFactory().registerSingleton("smsUsers", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return "mobile".equals(grant);
			}

			@Override
			public int getOrder() {
				return 100;
			}

			@Override
			public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String name) {
				return User.withUsername("sms-directory-user").password("{noop}correct-password").authorities("user")
						.build();
			}
		});
		request.setParameter("grant_type", "mobile");
		var authenticated = provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
		assertThat(authenticated.getName()).isEqualTo("alice");
	}

	@Test
	void missingPasswordCannotAuthenticateAFormUser() {
		assertThatThrownBy(() -> provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", null)))
						.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void formLoginWithCorrectPasswordDoesNotRequireClientCredentials() {
		request.removeParameter("client_id");
		assertThatCode(() -> {
			var authenticated = provider
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
			assertThat(authenticated.isAuthenticated()).isTrue();
			assertThat(authenticated.getName()).isEqualTo("alice");
		}).doesNotThrowAnyException();
	}

	@Test
	void passwordGrantStillAuthenticatesCorrectCredentials() {
		request.setParameter("grant_type", "password");
		var authenticated = provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
		assertThat(authenticated.isAuthenticated()).isTrue();
		assertThat(authenticated.getName()).isEqualTo("alice");
	}

	@Test
	void correctSmsCodeAuthenticatesAndAtomicallyConsumesStoredCode() {
		context.getBeanFactory().registerSingleton("smsUsers", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return "mobile".equals(grant);
			}

			@Override
			public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String name) {
				return User.withUsername("13800138000").password("{noop}ignored").authorities("user").build();
			}
		});
		when(valueOperations.getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000")).thenReturn("12345678");

		var token = new SmsAuthenticationToken("13800138000", "12345678");
		var authenticated = provider.authenticate(token);

		assertThat(authenticated.isAuthenticated()).isTrue();
		assertThat(authenticated.getName()).isEqualTo("13800138000");
		verify(valueOperations).getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000");
	}

	@Test
	void wrongSmsCodeIsRejected() {
		context.getBeanFactory().registerSingleton("smsUsers", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return "mobile".equals(grant);
			}

			@Override
			public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String name) {
				return User.withUsername("13800138000").password("{noop}ignored").authorities("user").build();
			}
		});
		when(valueOperations.getAndDelete(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000")).thenReturn("12345678");

		var token = new SmsAuthenticationToken("13800138000", "99999999");
		assertThatThrownBy(() -> provider.authenticate(token)).isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void failedLoginTracksCountInRedis() {
		when(valueOperations.increment(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, 1L) + "alice")).thenReturn(1L);

		assertThatThrownBy(() -> provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong-password")))
						.isInstanceOf(BadCredentialsException.class);

		verify(valueOperations).increment(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, 1L) + "alice");
		verify(redisTemplate).expire(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, 1L) + "alice", 30, java.util.concurrent.TimeUnit.MINUTES);
	}

	@Test
	void successfulLoginClearsFailureCounter() {
		var authenticated = provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password"));
		assertThat(authenticated.isAuthenticated()).isTrue();

		verify(redisTemplate).delete(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, 1L) + "alice");
	}

	@Test
	void redisLockoutBlocksAuthenticationAfterFiveFailures() {
		when(valueOperations.get(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, 1L) + "alice")).thenReturn(5L);

		assertThatThrownBy(() -> provider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct-password")))
						.isInstanceOf(LockedException.class);
	}

}
