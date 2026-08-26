package com.lotus.bixi.auth.service;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.TemporalAccessorUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.upms.api.service.TokenManagementService;
import com.lotus.bixi.upms.api.vo.TokenVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Primary
@Service
@RequiredArgsConstructor
public class LocalTokenManagementService implements TokenManagementService {

    private final OAuth2AuthorizationService authorizationService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CacheManager cacheManager;

    @Override
    public R<Page> getTokenPage(Map<String, Object> params) {
        String pattern = String.format("%s::*", CacheConstants.PROJECT_OAUTH_ACCESS);
        int current = MapUtil.getInt(params, CommonConstants.CURRENT);
        int size = MapUtil.getInt(params, CommonConstants.SIZE);

        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        }

        List<String> pages = keys.stream().skip((long) (current - 1) * size).limit(size).collect(Collectors.toList());
        Page<TokenVo> result = new Page<>(current, size);
        List<Object> authorizations = pages.isEmpty() ? List.of() : redisTemplate.opsForValue().multiGet(pages);
        if (authorizations == null) {
            authorizations = List.of();
        }

        List<TokenVo> tokenVoList = authorizations.stream()
                .filter(Objects::nonNull)
                .map(this::toTokenVo)
                .collect(Collectors.toList());
        result.setRecords(tokenVoList);
        result.setTotal(keys.size());
        return R.ok(result);
    }

    @Override
    public R<Boolean> removeTokenById(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return R.ok();
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null || StrUtil.isBlank(accessToken.getToken().getTokenValue())) {
            return R.ok();
        }

        Cache userDetailsCache = cacheManager.getCache(CacheConstants.USER_DETAILS);
        if (userDetailsCache != null) {
            userDetailsCache.evictIfPresent(authorization.getPrincipalName());
        }
        authorizationService.remove(authorization);
        SpringContextHolder.publishEvent(new LogoutSuccessEvent(new PreAuthenticatedAuthenticationToken(
                authorization.getPrincipalName(), authorization.getRegisteredClientId())));
        return R.ok();
    }

    @Override
    public R<Map<String, Object>> queryToken(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null) {
            return R.failed("invalid access token");
        }
        return R.ok(authorization.getAccessToken().getClaims());
    }

    private TokenVo toTokenVo(Object source) {
        OAuth2Authorization authorization = (OAuth2Authorization) source;
        TokenVo tokenVo = new TokenVo();
        tokenVo.setClientId(authorization.getRegisteredClientId());
        tokenVo.setId(authorization.getId());
        tokenVo.setUsername(authorization.getPrincipalName());

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        String tokenValue = accessToken.getToken().getTokenValue();
        tokenVo.setAccessToken(tokenValue.length() > 16
                ? tokenValue.substring(0, 8) + "..." + tokenValue.substring(tokenValue.length() - 8)
                : tokenValue);
        tokenVo.setExpiresAt(TemporalAccessorUtil.format(accessToken.getToken().getExpiresAt(),
                DatePattern.NORM_DATETIME_PATTERN));
        tokenVo.setIssuedAt(TemporalAccessorUtil.format(accessToken.getToken().getIssuedAt(),
                DatePattern.NORM_DATETIME_PATTERN));
        return tokenVo;
    }

}
