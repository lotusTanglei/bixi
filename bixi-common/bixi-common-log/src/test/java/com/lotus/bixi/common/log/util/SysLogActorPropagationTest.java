package com.lotus.bixi.common.log.util;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogEvent;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SysLogActorPropagationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuditConfiguration.class)
            .withBean(OperationLogService.class, () -> mock(OperationLogService.class))
            .withPropertyValues("spring.application.name=workflow-audit-test");

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncListenerPreservesTheOriginalActorAfterSecurityContextIsCleared(boolean servletRequest) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new Actor(7L), "", AuthorityUtils.NO_AUTHORITIES));
        if (servletRequest) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/business/approve");
            request.addParameter("operation", "complete");
            request.addParameter("password", "test-secret");
            request.addParameter("createBy", "999");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(BixiLogProperties.class).setExcludeFields(List.of("password"));
            // The source is captured on the caller thread, before the async listener is dispatched.
            var source = SysLogUtils.getSysLog();
            source.setTitle("完成任务");
            if (!servletRequest) {
                source.setBody(new AuditBody("complete", "test-secret"));
            }
            context.publishEvent(new SysLogEvent(source));
            OperationLogService persistence = context.getBean(OperationLogService.class);
            verifyNoInteractions(persistence);

            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
            DeferredExecutor executor = context.getBean(DeferredExecutor.class);
            assertThat(executor.pending).hasSize(1);
            executor.pending.remove(0).run();

            ArgumentCaptor<SysLog> savedLog = ArgumentCaptor.forClass(SysLog.class);
            verify(persistence).saveLog(savedLog.capture());
            assertThat(savedLog.getValue().getCreateBy()).isEqualTo(7L);
            assertThat(savedLog.getValue().getTitle()).isEqualTo("完成任务");
            assertThat(savedLog.getValue().getMethod()).isEqualTo(servletRequest ? "POST" : "LOCAL");
            assertThat(savedLog.getValue().getRequestUri()).isEqualTo(servletRequest ? "/business/approve" : "local");
            assertThat(savedLog.getValue().getParams()).contains("complete").doesNotContain("password", "test-secret");
        });
    }

    @ParameterizedTest
    @MethodSource("untrustedOrMissingActors")
    void eventsWithoutAnAuthenticatedActorKeepCreateByUnset(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(SysLogUtils.getSysLog().getCreateBy()).isNull();
        });
    }

    static Stream<Authentication> untrustedOrMissingActors() {
        return Stream.of(null,
                new AnonymousAuthenticationToken("anonymous", new Actor(99L),
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")),
                UsernamePasswordAuthenticationToken.unauthenticated(new Actor(99L), ""),
                UsernamePasswordAuthenticationToken.authenticated("principal-without-id", "",
                        AuthorityUtils.NO_AUTHORITIES));
    }

    public record Actor(Long id) {
        public Long getId() {
            return id;
        }
    }

    public record AuditBody(String operation, String password) {
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
            properties.setExcludeFields(List.of("password"));
            return new SysLogListener(persistence, properties);
        }

        @Bean("taskExecutor")
        DeferredExecutor taskExecutor() {
            return new DeferredExecutor();
        }

        @Bean
        BixiLogProperties logProperties() {
            return new BixiLogProperties();
        }

        @Bean
        SpringContextHolder contextHolder() {
            return new SpringContextHolder();
        }

        @Bean
        SpringUtil springUtil() {
            return new SpringUtil();
        }
    }
}
