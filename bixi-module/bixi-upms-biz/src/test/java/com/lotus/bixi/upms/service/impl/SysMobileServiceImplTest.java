package com.lotus.bixi.upms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import com.lotus.bixi.upms.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysMobileServiceImplTest {

	@BeforeEach
	void useDefaultTenant() {
		TenantContextHolder.set(1L);
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Mock
	SysUserMapper userMapper;

	@Mock
	RedisTemplate<String, Object> redisTemplate;

	@Mock
	ValueOperations<String, Object> valueOperations;

	@Mock
	SmsSender smsSender;

	@InjectMocks
	SysMobileServiceImpl service;

	@Test
	void unregisteredMobileCannotReceiveCode() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).contains("未注册");
		verifyNoInteractions(redisTemplate);
	}

	@Test
	void rateLimitedRequestCannotReceiveCode() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.hasKey(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				TenantContextHolder.get()) + "13800138000")).thenReturn(true);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).contains("频繁");
		verify(redisTemplate, never()).opsForValue();
	}

	@Test
	void successfulSendStoresCodeAndRateLimitInRedis() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.hasKey(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				TenantContextHolder.get()) + "13800138000")).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(smsSender.send(eq("13800138000"), anyString())).thenReturn(true);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();

		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(eq(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY,
				TenantContextHolder.get()) + "13800138000"), codeCaptor.capture(),
				eq(5L), eq(TimeUnit.MINUTES));
		verify(valueOperations).set(eq(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				TenantContextHolder.get()) + "13800138000"), eq("1"), eq(60L),
				eq(TimeUnit.SECONDS));

		String code = codeCaptor.getValue();
		assertThat(code).hasSize(8).matches("\\d+");
		verify(smsSender).send("13800138000", code);
	}

	@Test
	void smsSenderFailureStillReturnsSuccess() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.hasKey(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				TenantContextHolder.get()) + "13800138000")).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(smsSender.send(anyString(), anyString())).thenReturn(false);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();
		verify(valueOperations, times(2)).set(anyString(), any(), anyLong(), any(TimeUnit.class));
	}

}
