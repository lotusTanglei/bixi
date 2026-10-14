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
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysMobileServiceImplTest {

	@BeforeEach
	void useDefaultTenant() {
		TenantContextHolder.set(1L);
		service = new SysMobileServiceImpl(redisTemplate, userMapper, smsSender, Runnable::run);
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

	@Mock(answer = Answers.CALLS_REAL_METHODS)
	SmsSender smsSender;

	SysMobileServiceImpl service;

	@Test
	void malformedMobileNeverClaimsRedisKeyOrLooksUpUser() {
		for (String mobile : new String[] { null, "", "12800138000", "13800138000x",
				"13800138000".repeat(100) }) {
			R<Boolean> result = service.sendSmsCode(mobile);
			assertThat(result.getCode()).isNotZero();
		}
		verifyNoInteractions(redisTemplate, userMapper, smsSender);
	}

	@Test
	void configuredSenderHidesUnknownMobileWithoutCreatingCode() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS)).thenReturn(true);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();
		verify(valueOperations).setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS);
		verify(valueOperations, never()).set(startsWith(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY,
				1L)), any(), anyLong(), any(TimeUnit.class));
		verify(smsSender, never()).send(anyString(), anyString());
	}

	@Test
	void missingProviderGivesSameFailureForKnownAndUnknownMobiles() {
		SysMobileServiceImpl withoutProvider = new SysMobileServiceImpl(redisTemplate, userMapper,
				new NoopSmsSender(), Runnable::run);

		R<Boolean> unknown = withoutProvider.sendSmsCode("13800138000");
		R<Boolean> known = withoutProvider.sendSmsCode("13900139000");

		assertThat(unknown.getCode()).isEqualTo(1);
		assertThat(known.getCode()).isEqualTo(unknown.getCode());
		assertThat(unknown.getMsg()).isEqualTo("短信发送失败，请稍后再试");
		assertThat(known.getMsg()).isEqualTo(unknown.getMsg());
		verifyNoInteractions(userMapper, redisTemplate);
	}

	@Test
	void unsuccessfulRateLimitClaimStopsBeforeUserLookup() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS)).thenReturn(false, null);

		R<Boolean> occupied = service.sendSmsCode("13800138000");
		R<Boolean> indeterminate = service.sendSmsCode("13800138000");

		assertThat(occupied.getCode()).isEqualTo(1);
		assertThat(occupied.getMsg()).contains("频繁");
		assertThat(indeterminate.getCode()).isEqualTo(occupied.getCode());
		assertThat(indeterminate.getMsg()).isEqualTo(occupied.getMsg());
		verifyNoInteractions(userMapper);
		verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
	}

	@Test
	void successfulSendStoresCodeAndRateLimitInRedis() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
		when(smsSender.send(eq("13800138000"), anyString())).thenReturn(true);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();

		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(eq(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY,
				1L) + "13800138000"), codeCaptor.capture(),
				eq(5L), eq(TimeUnit.MINUTES));
		verify(valueOperations).setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				1L) + "13800138000", "1", 60L, TimeUnit.SECONDS);

		String code = codeCaptor.getValue();
		assertThat(code).hasSize(8).matches("\\d+");
		verify(smsSender).send("13800138000", code);
		InOrder lookupOrder = inOrder(valueOperations, userMapper);
		lookupOrder.verify(valueOperations).setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY,
				1L) + "13800138000", "1", 60L, TimeUnit.SECONDS);
		lookupOrder.verify(userMapper).selectOne(any(Wrapper.class));
	}

	@Test
	void smsSenderFailureClearsChallengeButDoesNotRevealMobile() {
		String smsKey = CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000";
		AtomicReference<String> storedCode = storedCodeFor(smsKey);
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
		when(smsSender.send(anyString(), anyString())).thenReturn(false);

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();
		assertThat(storedCode.get()).isNull();
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(smsKey)), anyString());
		verify(redisTemplate, never()).delete(smsKey);
		verify(redisTemplate, never()).delete(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000");
	}

	@Test
	void smsSenderExceptionClearsChallengeButDoesNotRevealMobile() {
		String smsKey = CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000";
		AtomicReference<String> storedCode = storedCodeFor(smsKey);
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000", "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
		when(smsSender.send(anyString(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));

		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isEqualTo(0);
		assertThat(result.getData()).isTrue();
		assertThat(storedCode.get()).isNull();
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(smsKey)), anyString());
		verify(redisTemplate, never()).delete(smsKey);
		verify(redisTemplate, never()).delete(CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, 1L)
				+ "13800138000");
	}

	@Test
	void failedOlderDeliveryDoesNotDeleteNewerChallenge() {
		String smsKey = CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L) + "13800138000";
		AtomicReference<String> storedCode = storedCodeFor(smsKey);
		AtomicReference<String> olderCode = new AtomicReference<>();
		AtomicReference<String> newerCode = new AtomicReference<>();
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
		when(smsSender.send(eq("13800138000"), anyString())).thenAnswer(invocation -> {
			String code = invocation.getArgument(1);
			String replacement = (code.startsWith("0") ? "1" : "0") + code.substring(1);
			olderCode.set(code);
			newerCode.set(replacement);
			storedCode.set(replacement);
			return false;
		});
		R<Boolean> result = service.sendSmsCode("13800138000");

		assertThat(result.getCode()).isZero();
		assertThat(storedCode.get()).isEqualTo(newerCode.get());
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(smsKey)), eq(olderCode.get()));
		verify(redisTemplate, never()).delete(smsKey);
	}

	@Test
	void configuredSenderDoesNotDelayTheRequest() throws Exception {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
		CountDownLatch deliveryStarted = new CountDownLatch(1);
		CountDownLatch releaseDelivery = new CountDownLatch(1);
		when(smsSender.send(eq("13800138000"), anyString())).thenAnswer(invocation -> {
			deliveryStarted.countDown();
			return releaseDelivery.await(10, TimeUnit.SECONDS);
		});
		ExecutorService requestThread = Executors.newSingleThreadExecutor();
		ExecutorService deliveryThread = Executors.newSingleThreadExecutor();
		SysMobileServiceImpl asyncService = new SysMobileServiceImpl(redisTemplate, userMapper, smsSender, deliveryThread);
		try {
			Future<R<Boolean>> response = requestThread.submit(() -> {
				TenantContextHolder.set(1L);
				try {
					return asyncService.sendSmsCode("13800138000");
				} finally {
					TenantContextHolder.clear();
				}
			});
			assertThat(response.get(2, TimeUnit.SECONDS).getCode()).isZero();
			assertThat(deliveryStarted.await(2, TimeUnit.SECONDS)).isTrue();
		} finally {
			releaseDelivery.countDown();
			requestThread.shutdownNow();
			deliveryThread.shutdown();
			assertThat(deliveryThread.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void unknownAtRequestTimeNeverReceivesCodeAfterLaterRegistration() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null, new SysUser());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
		Queue<Runnable> pending = new ArrayDeque<>();
		SysMobileServiceImpl queuedService = new SysMobileServiceImpl(redisTemplate, userMapper, smsSender, pending::add);

		R<Boolean> unknown = queuedService.sendSmsCode("13800138000");
		R<Boolean> registered = queuedService.sendSmsCode("13900139000");

		assertThat(unknown.getCode()).isZero();
		assertThat(registered.getCode()).isZero();
		assertThat(pending).hasSize(2);
		verify(valueOperations, never()).set(startsWith(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY,
				1L)), any(), anyLong(), any(TimeUnit.class));
		TenantContextHolder.set(2L);
		pending.remove().run();
		verify(smsSender, never()).send(anyString(), anyString());
		pending.remove().run();
		verify(valueOperations).set(eq(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, 1L)
				+ "13900139000"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
		verify(smsSender).send(eq("13900139000"), anyString());
	}

	@Test
	void fullDeliveryQueueRejectsKnownAndUnknownWithoutCreatingCode() {
		when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new SysUser(), null);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
		SysMobileServiceImpl rejectingService = new SysMobileServiceImpl(redisTemplate, userMapper, smsSender,
				task -> { throw new RejectedExecutionException("full"); });

		R<Boolean> known = rejectingService.sendSmsCode("13800138000");
		R<Boolean> unknown = rejectingService.sendSmsCode("13900139000");

		assertThat(known.getCode()).isEqualTo(unknown.getCode()).isEqualTo(1);
		assertThat(known.getMsg()).isEqualTo(unknown.getMsg());
		verify(valueOperations, never()).set(startsWith(CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY,
				1L)), any(), anyLong(), any(TimeUnit.class));
		verify(smsSender, never()).send(anyString(), anyString());
	}

	private AtomicReference<String> storedCodeFor(String smsKey) {
		AtomicReference<String> storedCode = new AtomicReference<>();
		doAnswer(invocation -> {
			storedCode.set(invocation.getArgument(1));
			return null;
		}).when(valueOperations).set(eq(smsKey), anyString(), eq(5L), eq(TimeUnit.MINUTES));
		when(redisTemplate.execute(any(RedisScript.class), eq(List.of(smsKey)), anyString()))
				.thenAnswer(invocation -> storedCode.compareAndSet(invocation.getArgument(2), null) ? 1L : 0L);
		return storedCode;
	}

}
