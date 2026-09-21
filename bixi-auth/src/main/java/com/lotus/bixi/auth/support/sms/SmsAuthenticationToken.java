package com.lotus.bixi.auth.support.sms;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * An unverified SMS credential. Only the SMS grant provider creates this token;
 * request parameters cannot turn a password authentication into SMS authentication.
 */
public final class SmsAuthenticationToken extends UsernamePasswordAuthenticationToken {

    public SmsAuthenticationToken(String mobile, String code) {
        super(mobile, code);
    }
}
