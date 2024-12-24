

package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.util.R;

/**
 * @author 唐磊
 * @date 2018/11/14
 */
public interface SysMobileService {

    /**
     * 发送手机验证码
     *
     * @param mobile mobile
     * @return code
     */
    R<Boolean> sendSmsCode(String mobile);

}
