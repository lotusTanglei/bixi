package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import com.lotus.bixi.upms.service.SmsSender;
import com.lotus.bixi.upms.service.SysMobileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service
public class SysMobileServiceImpl implements SysMobileService {

    private static final long CODE_TTL_MINUTES = 5;

    private static final long RATE_LIMIT_SECONDS = 60;

    private static final RedisScript<Long> DELETE_CODE_IF_UNCHANGED = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private final SysUserMapper sysUserMapper;

    private final SmsSender smsSender;

    private final Executor smsDeliveryExecutor;

    public SysMobileServiceImpl(RedisTemplate<String, Object> redisTemplate, SysUserMapper sysUserMapper,
                                SmsSender smsSender, @Qualifier("smsDeliveryExecutor") Executor smsDeliveryExecutor) {
        this.redisTemplate = redisTemplate;
        this.sysUserMapper = sysUserMapper;
        this.smsSender = smsSender;
        this.smsDeliveryExecutor = smsDeliveryExecutor;
    }

    @Override
    public R<Boolean> sendSmsCode(String mobile) {
        if (mobile == null || mobile.length() != 11 || !mobile.matches("1[3-9]\\d{9}")) {
            return R.failed("手机号格式不正确");
        }
        if (!smsSender.isConfigured()) {
            return R.failed("短信发送失败，请稍后再试");
        }

        String rateLimitKey = CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, TenantContextHolder.get()) + mobile;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(rateLimitKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS))) {
            return R.failed("验证码发送过于频繁，请稍后再试");
        }

        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, mobile));
        Long tenantId = TenantContextHolder.get();
        try {
            smsDeliveryExecutor.execute(() -> sendIfRegistered(mobile, tenantId, user != null));
        } catch (RejectedExecutionException rejected) {
            log.warn("SMS delivery queue unavailable");
            return R.failed("短信发送失败，请稍后再试");
        }
        return R.ok(Boolean.TRUE);
    }

    private void sendIfRegistered(String mobile, Long tenantId, boolean registered) {
        TenantContextHolder.clear();
        TenantContextHolder.set(tenantId);
        try {
            if (!registered) {
                return;
            }
            String code = RandomUtil.randomNumbers(Integer.parseInt(SecurityConstants.CODE_SIZE));
            String smsKey = CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, tenantId) + mobile;
            redisTemplate.opsForValue().set(smsKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

            boolean sent;
            try {
                sent = smsSender.send(mobile, code);
            } catch (RuntimeException failure) {
                sent = false;
            }
            if (!sent) {
                redisTemplate.execute(DELETE_CODE_IF_UNCHANGED, List.of(smsKey), code);
                log.warn("SMS delivery failed");
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

}
