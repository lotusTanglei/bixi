package com.lotus.bixi.upms.service;

/**
 * SMS delivery abstraction. Implementations send verification codes
 * through a specific provider (e.g. Aliyun SMS, Tencent Cloud SMS).
 *
 * @author 唐磊
 */
public interface SmsSender {

    /**
     * 发送短信验证码
     *
     * @param mobile 手机号
     * @param code   验证码
     * @return true 表示发送成功
     */
    boolean send(String mobile, String code);

}
