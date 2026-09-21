package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.service.BixiAppUserDetailsServiceImpl;
import com.lotus.bixi.common.security.service.BixiUserDetailsServiceImpl;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysMenu;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.UserQueryService;
import com.lotus.bixi.upms.mapper.SysMenuMapper;
import com.lotus.bixi.upms.mapper.SysRoleMenuMapper;
import com.lotus.bixi.upms.service.impl.SysMenuServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowUserCacheRestartTest {
    @ParameterizedTest
    @ValueSource(strings = {"cloud", "single"})
    void restartReloadsUsernameAndPhoneAuthoritiesWithoutDeletingSessions(String mode) {
        var caches = new ConcurrentMapCacheManager(CacheConstants.USER_DETAILS, CacheConstants.MENU_DETAILS, "sessions");
        caches.getCache("sessions").put("existing-token", "existing-authorization");
        // Keep one cache store across application restarts, like production Redis.
        for (boolean enabled : List.of(false, true, false, true)) {
            try (var context = new SpringApplicationBuilder(Config.class)
                    .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                    .initializers(app -> app.getBeanFactory().registerSingleton("cacheManager", caches))
                    .properties("spring.config.name=workflow-cache-restart-test",
                            "spring.cloud.nacos.config.enabled=false",
                            "spring.cloud.nacos.config.import-check.enabled=false",
                            "bixi.deployment.mode=" + mode, "workflow.enabled=" + enabled).run()) {
                var passwordUser = context.getBean(BixiUserDetailsServiceImpl.class).loadUserByUsername("admin");
                var phoneUser = context.getBean(BixiAppUserDetailsServiceImpl.class).loadUserByUsername("13800000000");
                for (var user : List.of(passwordUser, phoneUser)) {
                    var authorities = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
                    assertThat(authorities).contains("demo_task_view");
                    assertThat(authorities.contains("workflow_definition_edit")).isEqualTo(enabled);
                }
                assertThat(caches.getCache("sessions").get("existing-token", String.class))
                        .isEqualTo("existing-authorization");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @ComponentScan("com.lotus.bixi.upms.config")
    @Import(SysMenuServiceImpl.class)
    static class Config {
        @Bean SysRoleMenuMapper roleMenuMapper() { return mock(SysRoleMenuMapper.class); }
        @Bean SysMenuMapper menuMapper() {
            var mapper = mock(SysMenuMapper.class);
            var core = new SysMenu(); core.setId(1L); core.setPermission("demo_task_view");
            var workflow = new SysMenu(); workflow.setId(2L); workflow.setPermission("workflow_definition_edit");
            when(mapper.listMenusByRoleId(1L)).thenReturn(List.of(core, workflow));
            return mapper;
        }
        @Bean UserQueryService userQueryService(SysMenuService menus) {
            var query = mock(UserQueryService.class);
            when(query.info(any())).thenAnswer(invocation -> {
                var user = new SysUser(); user.setId(1L); user.setUsername("admin");
                user.setPhone("13800000000"); user.setPassword("test-hash");
                user.setStatus("0"); user.setLockFlag("0");
                var info = new UserInfo(); info.setSysUser(user); info.setRoles(new Long[]{1L});
                info.setPermissions(menus.findMenuByRoleId(1L).stream().map(SysMenu::getPermission).toArray(String[]::new));
                return R.ok(info);
            });
            return query;
        }
        @Bean BixiUserDetailsServiceImpl usernameUsers(UserQueryService query, CacheManager caches) {
            return new BixiUserDetailsServiceImpl(query, caches);
        }
        @Bean BixiAppUserDetailsServiceImpl phoneUsers(UserQueryService query, CacheManager caches) {
            return new BixiAppUserDetailsServiceImpl(query, caches);
        }
    }
}
