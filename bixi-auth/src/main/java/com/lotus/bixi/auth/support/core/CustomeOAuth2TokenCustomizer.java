package com.lotus.bixi.auth.support.core;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.security.service.BixiUser;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.HashMap;
import java.util.Map;

/**
 * token 输出增强
 * 注意：只将必要的信息放入 Claims，避免敏感信息泄露
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public class CustomeOAuth2TokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {

    /**
     * Customize the OAuth 2.0 Token attributes.
     *
     * @param context the context containing the OAuth 2.0 Token attributes
     */
    @Override
    public void customize(OAuth2TokenClaimsContext context) {
        OAuth2TokenClaimsSet.Builder claims = context.getClaims();
        claims.claim(SecurityConstants.DETAILS_LICENSE, SecurityConstants.PROJECT_LICENSE);
        String clientId = context.getAuthorizationGrant().getName();
        claims.claim(SecurityConstants.CLIENT_ID, clientId);

        // 客户端模式不返回具体用户信息
        if (SecurityConstants.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType().getValue())) {
            return;
        }

        BixiUser bixiUser = (BixiUser) context.getPrincipal().getPrincipal();

        // 只存储必要的用户标识信息，不存储完整用户对象
        // 敏感信息（如手机号）不应放入 Token
        claims.claim(SecurityConstants.DETAILS_USER_ID, bixiUser.getId());
        claims.claim(SecurityConstants.USERNAME, bixiUser.getUsername());

        // 存储部门ID用于数据权限控制
        Map<String, Object> userDetails = new HashMap<>(2);
        userDetails.put("id", bixiUser.getId());
        userDetails.put("deptId", bixiUser.getDeptId());
        claims.claim(SecurityConstants.DETAILS_USER, userDetails);
    }

}
