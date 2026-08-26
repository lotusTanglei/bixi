package com.lotus.bixi.auth.endpoint;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.auth.support.handler.BixiAuthenticationFailureEventHandler;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.util.RetOps;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.common.security.util.OAuth2EndpointUtils;
import com.lotus.bixi.common.security.util.OAuth2ErrorCodesExpand;
import com.lotus.bixi.common.security.util.OAuthClientException;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.api.service.TokenManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

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

    private final ClientDetailsQueryService clientDetailsService;

    private final TokenManagementService tokenManagementService;

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
        return tokenManagementService.removeTokenById(token);
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
        return tokenManagementService.getTokenPage(params);
    }

    @Inner
    @GetMapping("/query-token")
    public R<Map<String, Object>> queryToken(@RequestParam String token) {
        return tokenManagementService.queryToken(token);
    }

}
