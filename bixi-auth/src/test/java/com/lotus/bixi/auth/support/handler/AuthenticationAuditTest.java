package com.lotus.bixi.auth.support.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.auth.service.LocalTokenManagementService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.security.Principal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationAuditTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuditConfiguration.class)
            .withBean(BixiLogProperties.class)
            .withBean(SpringContextHolder.class)
            .withBean(SpringUtil.class)
            .withBean(BixiLogoutSuccessEventHandler.class)
            .withBean(OperationLogService.class, () -> mock(OperationLogService.class))
            .withPropertyValues("spring.application.name=auth-audit-test");

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void logoutAuditPreservesMetadataAndSafeParametersWithoutSavingTheAuthorizationHeader() {
        String token = "credential-access-token";
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/token/logout");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.addParameter("reason", "user-requested");
        request.addParameter("access_token", token);
        request.addParameter("password", "credential-password");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var authentication = new PreAuthenticatedAuthenticationToken(new Actor(7L), "browser-client",
                AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            context.publishEvent(new LogoutSuccessEvent(authentication));
            SysLog saved = flushAudit(context.getBean(DeferredExecutor.class),
                    context.getBean(OperationLogService.class));

            assertThat(saved.getParams()).doesNotContain("Bearer", token, "credential-password")
                    .contains("reason=", "user-requested");
            assertThat(saved.getTitle()).isEqualTo("退出成功");
            assertThat(saved.getCreateBy()).isEqualTo(7L);
            assertThat(saved.getServiceId()).isEqualTo("browser-client");
            assertThat(saved.getMethod()).isEqualTo("DELETE");
            assertThat(saved.getRequestUri()).isEqualTo("/token/logout");
            assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        });
    }

    @Test
    void publicLogoutRecordsTheStoredTokenOwnerWithoutARequestSecurityContext() {
        String token = "stored-owner-access-token";
        var request = new MockHttpServletRequest("DELETE", "/token/logout");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.clearContext();
        var owner = new BixiUser(7L, 1L, "owner", "unused", null,
                true, true, true, true, AuthorityUtils.NO_AUTHORITIES);
        var identity = UsernamePasswordAuthenticationToken.authenticated(owner, "unused", owner.getAuthorities());
        var client = RegisteredClient.withId("browser-client").clientId("browser-client")
                .authorizationGrantType(AuthorizationGrantType.PASSWORD).build();
        var authorization = OAuth2Authorization.withRegisteredClient(client).principalName(owner.getUsername())
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .attribute(Principal.class.getName(), identity)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(60))).build();
        var authorizations = mock(OAuth2AuthorizationService.class);
        when(authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);

        runner.run(context -> {
            var service = new LocalTokenManagementService(authorizations, mock(RedisTemplate.class), mock(CacheManager.class));
            assertThat(service.removeTokenById(token).getCode()).isZero();
            verify(authorizations).remove(authorization);
            SysLog saved = flushAudit(context.getBean(DeferredExecutor.class), context.getBean(OperationLogService.class));
            assertThat(saved.getCreateBy()).isEqualTo(7L);
            assertThat(saved.getTitle()).isEqualTo("退出成功");
            assertThat(saved.getParams()).doesNotContain(token);
            assertThat(saved.getServiceId()).isEqualTo("browser-client");
        });
    }

    @Test
    void invalidTokenFailureAuditExcludesTheTokenRequestParameter() {
        String token = "credential-invalid-token";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/token/check_token");
        request.addParameter("token", token);
        request.addParameter("operation", "check");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            new BixiAuthenticationFailureEventHandler().onAuthenticationFailure(request, response,
                    new InvalidBearerTokenException("Invalid bearer token"));
            SysLog saved = flushAudit(context.getBean(DeferredExecutor.class),
                    context.getBean(OperationLogService.class));

            assertThat(saved.getParams()).doesNotContain(token).contains("operation=", "check");
            assertThat(saved.getTitle()).isEqualTo("登录失败");
            assertThat(saved.getCreateBy()).isNull();
            assertThat(saved.getServiceId()).isEqualTo("auth-audit-test");
            assertThat(saved.getException()).isEqualTo("Invalid bearer token");
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(request.getParameter("token")).isEqualTo(token);
        });
    }

    private static SysLog flushAudit(DeferredExecutor executor, OperationLogService persistence) {
        verifyNoInteractions(persistence);
        assertThat(executor.pending).hasSize(1);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        executor.pending.remove(0).run();
        ArgumentCaptor<SysLog> saved = ArgumentCaptor.forClass(SysLog.class);
        verify(persistence).saveLog(saved.capture());
        return saved.getValue();
    }

    public record Actor(Long id) {
        public Long getId() {
            return id;
        }
    }

    static class DeferredExecutor implements Executor {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            pending.add(task);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class AuditConfiguration {
        @Bean
        SysLogListener listener(OperationLogService persistence, BixiLogProperties properties) {
            return new SysLogListener(persistence, properties);
        }

        @Bean("taskExecutor")
        DeferredExecutor taskExecutor() {
            return new DeferredExecutor();
        }
    }
}
