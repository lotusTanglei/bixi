package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.service.SysMobileService;
import org.springframework.stereotype.Service;

/** SMS delivery remains unavailable until a real provider is configured. */
@Service
public class SysMobileServiceImpl implements SysMobileService {

    @Override
    public R<Boolean> sendSmsCode(String mobile) {
        // Never generate a login credential that has not been delivered out of band.
        // A future sender must store successfully delivered challenges under CacheConstants.SMS_CODE_KEY.
        return R.failed("短信发送服务未配置，暂不支持短信登录");
    }
}
