package com.lotus.bixi.auth.endpoint;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.TemporalAccessorUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.auth.support.handler.BixiAuthenticationFailureEventHandler;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.util.RetOps;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.common.security.util.OAuth2EndpointUtils;
import com.lotus.bixi.common.security.util.OAuth2ErrorCodesExpand;
import com.lotus.bixi.common.security.util.OAuthClientException;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.feign.RemoteClientDetailsService;
import com.lotus.bixi.upms.api.vo.TokenVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/token")
public class BixiTokenEndpoint {

    private final HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenHttpResponseConverter = new OAuth2AccessTokenResponseHttpMessageConverter();

    private final AuthenticationFailureHandler authenticationFailureHandler = new BixiAuthenticationFailureEventHandler();

    private final OAuth2AuthorizationService authorizationService;

    private final RemoteClientDetailsService clientDetailsService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CacheManager cacheManager;

    /**
     * 认证页面
     *
     * @param modelAndView
     * @param error        表单登录失败处理回调的错误信息
     * @return ModelAndView
     */
    @GetMapping("/login")
    public ModelAndView require(ModelAndView modelAndView, @RequestParam(required = false) String error) {
        modelAndView.setViewName("ftl/login");
        modelAndView.addObject("error", error);
        return modelAndView;
    }

    @GetMapping("/confirm_access")
    public ModelAndView confirm(Principal principal, ModelAndView modelAndView,
                                @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
                                @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
                                @RequestParam(OAuth2ParameterNames.STATE) String state) {
        SysOauthClientDetails clientDetails = RetOps.of(clientDetailsService.getClientDetailsById(clientId))
                .getData()
                .orElseThrow(() -> new OAuthClientException("clientId 不合法"));

        Set<String> authorizedScopes = StringUtils.commaDelimitedListToSet(clientDetails.getScope());
        modelAndView.addObject("clientId", clientId);
        modelAndView.addObject("state", state);
        modelAndView.addObject("scopeList", authorizedScopes);
        modelAndView.addObject("principalName", principal.getName());
        modelAndView.setViewName("ftl/confirm");
        return modelAndView;
    }

    /**
     * 退出并删除token
     *
     * @param authHeader Authorization
     */
    @DeleteMapping("/logout")
    public R<Boolean> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (StrUtil.isBlank(authHeader)) {
            return R.ok();
        }

        String tokenValue = authHeader.replace(OAuth2AccessToken.TokenType.BEARER.getValue(), StrUtil.EMPTY).trim();
        return removeToken(tokenValue);
    }

    /**
     * 校验token (仅内部服务调用)
     *
     * @param token 令牌
     * @param response 响应
     * @param request 请求
     */
    @Inner
    @SneakyThrows
    @GetMapping("/check_token")
    public void checkToken(String token, HttpServletResponse response, HttpServletRequest request) {
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);

        if (StrUtil.isBlank(token)) {
            httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
            this.authenticationFailureHandler.onAuthenticationFailure(request, response,
                    new InvalidBearerTokenException(OAuth2ErrorCodesExpand.TOKEN_MISSING));
            return;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);

        // 如果令牌不存在 返回401
        if (authorization == null || authorization.getAccessToken() == null) {
            this.authenticationFailureHandler.onAuthenticationFailure(request, response,
                    new InvalidBearerTokenException(OAuth2ErrorCodesExpand.INVALID_BEARER_TOKEN));
            return;
        }

        Map<String, Object> claims = authorization.getAccessToken().getClaims();
        OAuth2AccessTokenResponse sendAccessTokenResponse = OAuth2EndpointUtils.sendAccessTokenResponse(authorization,
                claims);
        this.accessTokenHttpResponseConverter.write(sendAccessTokenResponse, MediaType.APPLICATION_JSON, httpResponse);
    }

    /**
     * 令牌管理调用
     *
     * @param token token
     */
    @Inner
    @DeleteMapping("/remove/{token}")
    public R<Boolean> removeToken(@PathVariable("token") String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return R.ok();
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null || StrUtil.isBlank(accessToken.getToken().getTokenValue())) {
            return R.ok();
        }
        // 清空用户信息（立即删除）
        cacheManager.getCache(CacheConstants.USER_DETAILS).evictIfPresent(authorization.getPrincipalName());
        // 清空access token
        authorizationService.remove(authorization);
        // 处理自定义退出事件，保存相关日志
        SpringContextHolder.publishEvent(new LogoutSuccessEvent(new PreAuthenticatedAuthenticationToken(
                authorization.getPrincipalName(), authorization.getRegisteredClientId())));
        return R.ok();
    }

    /**
     * 查询token
     *
     * @param params 分页参数
     * @return
     */
    @Inner
    @PostMapping("/page")
    public R<Page> tokenList(@RequestBody Map<String, Object> params) {
        // 根据分页参数获取对应数据
        String pattern = String.format("%s::*", CacheConstants.PROJECT_OAUTH_ACCESS);
        int current = MapUtil.getInt(params, CommonConstants.CURRENT);
        int size = MapUtil.getInt(params, CommonConstants.SIZE);

        // 使用 SCAN 替代 KEYS 命令，避免在大数据量下阻塞 Redis
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        }

        List<String> pages = keys.stream().skip((current - 1) * size).limit(size).collect(Collectors.toList());
        Page result = new Page(current, size);

        List<TokenVo> tokenVoList = redisTemplate.opsForValue().multiGet(pages).stream().filter(Objects::nonNull).map(obj -> {
            OAuth2Authorization authorization = (OAuth2Authorization) obj;
            TokenVo tokenVo = new TokenVo();
            tokenVo.setClientId(authorization.getRegisteredClientId());
            tokenVo.setId(authorization.getId());
            tokenVo.setUsername(authorization.getPrincipalName());
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();

            // Token 值截断处理，仅显示前8位和后8位
            String tokenValue = accessToken.getToken().getTokenValue();
            tokenVo.setAccessToken(tokenValue.length() > 16
                    ? tokenValue.substring(0, 8) + "..." + tokenValue.substring(tokenValue.length() - 8)
                    : tokenValue);

            String expiresAt = TemporalAccessorUtil.format(accessToken.getToken().getExpiresAt(),
                    DatePattern.NORM_DATETIME_PATTERN);
            tokenVo.setExpiresAt(expiresAt);

            String issuedAt = TemporalAccessorUtil.format(accessToken.getToken().getIssuedAt(),
                    DatePattern.NORM_DATETIME_PATTERN);
            tokenVo.setIssuedAt(issuedAt);
            return tokenVo;
        }).collect(Collectors.toList());
        result.setRecords(tokenVoList);
        result.setTotal(keys.size());
        return R.ok(result);
    }

}
