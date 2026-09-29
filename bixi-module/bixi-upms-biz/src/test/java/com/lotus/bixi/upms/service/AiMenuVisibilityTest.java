package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.upms.api.entity.SysMenu;
import com.lotus.bixi.upms.mapper.SysMenuMapper;
import com.lotus.bixi.upms.mapper.SysRoleMenuMapper;
import com.lotus.bixi.upms.service.impl.SysMenuServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMenuVisibilityTest {

    @Test
    void disabledAiRemovesRoutesAndPermissions() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("ai.enabled=false")
                .run(context -> assertThat(context.getBean(SysMenuService.class).findMenuByRoleId(1L))
                        .noneMatch(AiMenuVisibilityTest::isAiMenu));
    }

    @Test
    void aiToggleUsesASeparateMenuCacheVariant() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("ai.enabled=true")
                .run(context -> {
                    var service = context.getBean(SysMenuService.class);
                    var menus = service.findMenuByRoleId(1L);
                    assertThat(menus).hasSize(4);

                    context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("ai-toggle", Map.of("ai.enabled", "false")));
                    assertThat(service.findMenuByRoleId(1L)).noneMatch(AiMenuVisibilityTest::isAiMenu);

                    context.getEnvironment().getPropertySources().remove("ai-toggle");
                    assertThat(service.findMenuByRoleId(1L)).hasSize(4);
                });
    }

    private static boolean isAiMenu(SysMenu menu) {
        String path = menu.getPath() == null ? "" : menu.getPath();
        String permission = menu.getPermission() == null ? "" : menu.getPermission();
        return path.equals("/ai") || path.startsWith("/ai/")
                || permission.startsWith("ai_") || permission.startsWith("ai:");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(SysMenuServiceImpl.class)
    static class Config {
        @Bean
        SysMenuMapper menuMapper() {
            var mapper = mock(SysMenuMapper.class);
            var core = menu(1L, "/demo/task/index", "demo_task_view");
            var aiRoot = menu(2L, "/ai", null);
            var aiPage = menu(3L, "/ai/chat", null);
            var aiButton = menu(4L, null, "ai_chat_add");
            when(mapper.listMenusByRoleId(1L)).thenReturn(List.of(core, aiRoot, aiPage, aiButton));
            return mapper;
        }

        @Bean
        SysRoleMenuMapper roleMenuMapper() {
            return mock(SysRoleMenuMapper.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConstants.MENU_DETAILS);
        }
    }

    private static SysMenu menu(long id, String path, String permission) {
        var menu = new SysMenu();
        menu.setId(id);
        menu.setPath(path);
        menu.setPermission(permission);
        return menu;
    }
}
