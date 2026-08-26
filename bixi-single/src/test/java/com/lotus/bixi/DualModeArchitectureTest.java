package com.lotus.bixi;

import com.lotus.bixi.auth.service.LocalTokenManagementService;
import com.lotus.bixi.upms.api.feign.RemoteClientDetailsService;
import com.lotus.bixi.upms.api.feign.RemoteDictService;
import com.lotus.bixi.upms.api.feign.RemoteLogService;
import com.lotus.bixi.upms.api.feign.RemoteParamService;
import com.lotus.bixi.upms.api.feign.RemoteTokenService;
import com.lotus.bixi.upms.api.feign.RemoteUserService;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.api.service.DictionaryQueryService;
import com.lotus.bixi.upms.api.service.OperationLogService;
import com.lotus.bixi.upms.api.service.PublicParamQueryService;
import com.lotus.bixi.upms.api.service.TokenManagementService;
import com.lotus.bixi.upms.api.service.UserQueryService;
import com.lotus.bixi.upms.service.SysDictItemService;
import com.lotus.bixi.upms.service.SysLogService;
import com.lotus.bixi.upms.service.SysOauthClientDetailsService;
import com.lotus.bixi.upms.service.SysPublicParamService;
import com.lotus.bixi.upms.service.SysUserService;
import com.lotus.bixi.upms.service.local.LocalClientDetailsQueryService;
import com.lotus.bixi.upms.service.local.LocalDictionaryQueryService;
import com.lotus.bixi.upms.service.local.LocalOperationLogService;
import com.lotus.bixi.upms.service.local.LocalPublicParamQueryService;
import com.lotus.bixi.upms.service.local.LocalUserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DualModeArchitectureTest {

    @Test
    void feignAdaptersDoNotOverrideLocalPrimaryAdapters() {
        Class<?>[] feignAdapters = {
                RemoteUserService.class,
                RemoteClientDetailsService.class,
                RemoteDictService.class,
                RemoteParamService.class,
                RemoteLogService.class,
                RemoteTokenService.class
        };

        for (Class<?> feignAdapter : feignAdapters) {
            assertThat(feignAdapter.getAnnotation(FeignClient.class).primary())
                    .as("%s must not be a primary bean", feignAdapter.getSimpleName())
                    .isFalse();
        }
    }

    @Test
    void localAdaptersAreSelectedWhenBothTransportsExist() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AdapterTestConfiguration.class)) {
            assertThat(context.getBean(UserQueryService.class)).isInstanceOf(LocalUserQueryService.class);
            assertThat(context.getBean(ClientDetailsQueryService.class))
                    .isInstanceOf(LocalClientDetailsQueryService.class);
            assertThat(context.getBean(DictionaryQueryService.class)).isInstanceOf(LocalDictionaryQueryService.class);
            assertThat(context.getBean(PublicParamQueryService.class))
                    .isInstanceOf(LocalPublicParamQueryService.class);
            assertThat(context.getBean(OperationLogService.class)).isInstanceOf(LocalOperationLogService.class);
            assertThat(context.getBean(TokenManagementService.class)).isInstanceOf(LocalTokenManagementService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            LocalUserQueryService.class,
            LocalClientDetailsQueryService.class,
            LocalDictionaryQueryService.class,
            LocalPublicParamQueryService.class,
            LocalOperationLogService.class,
            LocalTokenManagementService.class
    })
    static class AdapterTestConfiguration {

        @Bean
        SysUserService sysUserService() {
            return mock(SysUserService.class);
        }

        @Bean
        SysOauthClientDetailsService sysOauthClientDetailsService() {
            return mock(SysOauthClientDetailsService.class);
        }

        @Bean
        SysDictItemService sysDictItemService() {
            return mock(SysDictItemService.class);
        }

        @Bean
        SysPublicParamService sysPublicParamService() {
            return mock(SysPublicParamService.class);
        }

        @Bean
        SysLogService sysLogService() {
            return mock(SysLogService.class);
        }

        @Bean
        OAuth2AuthorizationService authorizationService() {
            return mock(OAuth2AuthorizationService.class);
        }

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        CacheManager cacheManager() {
            return mock(CacheManager.class);
        }

        @Bean
        RemoteUserService remoteUserService() {
            return mock(RemoteUserService.class);
        }

        @Bean
        RemoteClientDetailsService remoteClientDetailsService() {
            return mock(RemoteClientDetailsService.class);
        }

        @Bean
        RemoteDictService remoteDictService() {
            return mock(RemoteDictService.class);
        }

        @Bean
        RemoteParamService remoteParamService() {
            return mock(RemoteParamService.class);
        }

        @Bean
        RemoteLogService remoteLogService() {
            return mock(RemoteLogService.class);
        }

        @Bean
        RemoteTokenService remoteTokenService() {
            return mock(RemoteTokenService.class);
        }

    }

}
