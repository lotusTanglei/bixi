package com.lotus.bixi.auth.support.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.auth.support.sms.SmsAuthenticationToken;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
public class BixiDaoAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    /**
     * The plaintext password used to perform PasswordEncoder#matches(CharSequence,
     * String)} on when the user is not found to avoid SEC-2056.
     */
    private static final String USER_NOT_FOUND_PASSWORD = "userNotFoundPassword";

    private PasswordEncoder passwordEncoder;

    /**
     * The password used to perform {@link PasswordEncoder#matches(CharSequence, String)}
     * on when the user is not found to avoid SEC-2056. This is necessary, because some
     * {@link PasswordEncoder} implementations will short circuit if the password is not
     * in a valid format.
     */
    private volatile String userNotFoundEncodedPassword;

    private UserDetailsService userDetailsService;

    private UserDetailsPasswordService userDetailsPasswordService;

    public BixiDaoAuthenticationProvider() {
        setMessageSource(SpringUtil.getBean("securityMessageSource"));
        setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {

        if (authentication instanceof SmsAuthenticationToken) {
            checkSmsCode(authentication);
            return;
        }

        if (authentication.getCredentials() == null) {
            this.logger.debug("Failed to authenticate since no credentials provided");
            throw new BadCredentialsException(this.messages
                    .getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }
        String presentedPassword = authentication.getCredentials().toString();
        if (!this.passwordEncoder.matches(presentedPassword, userDetails.getPassword())) {
            this.logger.debug("Failed to authenticate since password does not match stored value");
            throw new BadCredentialsException(this.messages
                    .getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }
    }

    @SuppressWarnings("unchecked")
    private void checkSmsCode(UsernamePasswordAuthenticationToken authentication) {
        String code = authentication.getCredentials() == null ? null : authentication.getCredentials().toString();
        if (StrUtil.isBlank(authentication.getName()) || StrUtil.isBlank(code)) {
            throw new BadCredentialsException("短信验证码不合法");
        }
        RedisTemplate<String, Object> redis = SpringUtil.getBean(RedisTemplate.class);
        // GETDEL makes the challenge single-use even when concurrent requests arrive.
        Object saved = redis.opsForValue().getAndDelete(CacheConstants.SMS_CODE_KEY + authentication.getName());
        if (saved == null || !code.equals(saved.toString())) {
            throw new BadCredentialsException("短信验证码不合法");
        }
    }

    @Override
    protected final UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) {
        prepareTimingAttackProtection();
        String grantType = authentication instanceof SmsAuthenticationToken
                ? SecurityConstants.MOBILE : AuthorizationGrantType.PASSWORD.getValue();
        // OAuth providers attach the authenticated client ID; ordinary form login has no client.
        String clientId = authentication.getDetails() instanceof String id ? id : null;

        Map<String, BixiUserDetailsService> userDetailsServiceMap = SpringUtil
                .getBeansOfType(BixiUserDetailsService.class);

        String finalClientId = clientId;
        Optional<BixiUserDetailsService> optional = userDetailsServiceMap.values()
                .stream()
                .filter(service -> service.support(finalClientId, grantType))
                .max(Comparator.comparingInt(Ordered::getOrder));

        if (optional.isEmpty()) {
            throw new InternalAuthenticationServiceException("UserDetailsService error , not register");
        }

        try {
            UserDetails loadedUser = optional.get().loadUserByUsername(username);
            if (loadedUser == null) {
                throw new InternalAuthenticationServiceException(
                        "UserDetailsService returned null, which is an interface contract violation");
            }
            return loadedUser;
        } catch (UsernameNotFoundException ex) {
            mitigateAgainstTimingAttack(authentication);
            throw ex;
        } catch (InternalAuthenticationServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        }
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
                                                         UserDetails user) {
        boolean upgradeEncoding = !(authentication instanceof SmsAuthenticationToken)
                && this.userDetailsPasswordService != null
                && this.passwordEncoder.upgradeEncoding(user.getPassword());
        if (upgradeEncoding) {
            String presentedPassword = authentication.getCredentials().toString();
            String newPassword = this.passwordEncoder.encode(presentedPassword);
            user = this.userDetailsPasswordService.updatePassword(user, newPassword);
        }
        return super.createSuccessAuthentication(principal, authentication, user);
    }

    private void prepareTimingAttackProtection() {
        if (this.userNotFoundEncodedPassword == null) {
            this.userNotFoundEncodedPassword = this.passwordEncoder.encode(USER_NOT_FOUND_PASSWORD);
        }
    }

    private void mitigateAgainstTimingAttack(UsernamePasswordAuthenticationToken authentication) {
        if (authentication.getCredentials() != null) {
            String presentedPassword = authentication.getCredentials().toString();
            this.passwordEncoder.matches(presentedPassword, this.userNotFoundEncodedPassword);
        }
    }

    /**
     * Sets the PasswordEncoder instance to be used to encode and validate passwords. If
     * not set, the password will be compared using
     * {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}
     *
     * @param passwordEncoder must be an instance of one of the {@code PasswordEncoder}
     *                        types.
     */
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        Assert.notNull(passwordEncoder, "passwordEncoder cannot be null");
        this.passwordEncoder = passwordEncoder;
        this.userNotFoundEncodedPassword = null;
    }

    protected PasswordEncoder getPasswordEncoder() {
        return this.passwordEncoder;
    }

    public void setUserDetailsService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    protected UserDetailsService getUserDetailsService() {
        return this.userDetailsService;
    }

    public void setUserDetailsPasswordService(UserDetailsPasswordService userDetailsPasswordService) {
        this.userDetailsPasswordService = userDetailsPasswordService;
    }

}
