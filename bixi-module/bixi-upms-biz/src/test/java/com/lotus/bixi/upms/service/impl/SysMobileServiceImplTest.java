package com.lotus.bixi.upms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.util.RedisUtils;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysMobileServiceImplTest {
    @Mock SysUserMapper userMapper;
    @InjectMocks SysMobileServiceImpl service;

    @Test
    void missingSmsSenderFailsClosedInsteadOfReturningAnAuthenticationCode() {
        lenient().when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysUser()));
        try (var redis = mockStatic(RedisUtils.class)) {
            var result = service.sendSmsCode("13800138000");
            assertThat(result.getCode()).isEqualTo(CommonConstants.FAIL);
            assertThat(result.getData()).isNull();
            assertThat(result.getMsg()).contains("短信", "未配置");
        }
    }

    @Test
    void missingSmsSenderDoesNotCreateAnUndeliverableCode() {
        lenient().when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysUser()));
        try (var redis = mockStatic(RedisUtils.class)) {
            service.sendSmsCode("13800138000");
            redis.verifyNoInteractions();
        }
    }
}
