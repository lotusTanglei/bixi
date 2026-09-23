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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysMobileServiceImpl implements SysMobileService {

    private static final long CODE_TTL_MINUTES = 5;

    private static final long RATE_LIMIT_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;

    private final SysUserMapper sysUserMapper;

    private final SmsSender smsSender;

    @Override
    public R<Boolean> sendSmsCode(String mobile) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, mobile));
        if (user == null) {
            return R.failed("手机号未注册");
        }

        String rateLimitKey = CacheConstants.tenantKey(CacheConstants.SMS_RATE_LIMIT_KEY, TenantContextHolder.get()) + mobile;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            return R.failed("验证码发送过于频繁，请稍后再试");
        }

        String code = RandomUtil.randomNumbers(Integer.parseInt(SecurityConstants.CODE_SIZE));
        String smsKey = CacheConstants.tenantKey(CacheConstants.SMS_CODE_KEY, TenantContextHolder.get()) + mobile;
        redisTemplate.opsForValue().set(smsKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(rateLimitKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        boolean sent = smsSender.send(mobile, code);
        if (!sent) {
            log.warn("SMS delivery failed for {}, code stored in Redis for testing", mobile);
        }
        return R.ok(Boolean.TRUE);
    }

}
