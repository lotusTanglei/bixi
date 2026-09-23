

package com.lotus.bixi.common.security.service;

import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 用户详细信息
 *
 * @author 唐磊 hccake
 */
@Slf4j
@Primary
@RequiredArgsConstructor
public class BixiUserDetailsServiceImpl implements BixiUserDetailsService {

    private final UserQueryService userQueryService;

    private final CacheManager cacheManager;

    /**
     * 用户名密码登录
     *
     * @param username 用户名
     * @return
     */
    @Override
    @SneakyThrows
    public UserDetails loadUserByUsername(String username) {
        String cacheKey = CacheConstants.tenantKey(CacheConstants.USER_DETAILS, TenantContextHolder.get()) + username;
        Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
        if (cache != null && cache.get(cacheKey) != null) {
            return (BixiUser) cache.get(cacheKey).get();
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        R<UserInfo> result = userQueryService.info(userDTO);
        UserDetails userDetails = getUserDetails(result);
        if (cache != null) {
            cache.put(cacheKey, userDetails);
        }
        return userDetails;
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

}
