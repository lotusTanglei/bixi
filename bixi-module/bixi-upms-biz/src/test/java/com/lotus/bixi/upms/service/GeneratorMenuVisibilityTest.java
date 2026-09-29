package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.upms.api.entity.SysMenu;
import com.lotus.bixi.upms.mapper.SysMenuMapper;
import com.lotus.bixi.upms.mapper.SysRoleMenuMapper;
import com.lotus.bixi.upms.service.impl.SysMenuServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratorMenuVisibilityTest {

    @Test
    void disabledGeneratorRemovesGeneratorMenusAndButtons() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("workflow.enabled=true", "generator.enabled=false")
                .run(context -> {
                    var service = context.getBean(SysMenuService.class);

                    assertThat(service.findMenuByRoleId(1L))
                            .noneMatch(menu -> isGeneratorMenu(menu));
                });
    }

    @Test
    void generatorToggleDoesNotReuseMenusFromTheOtherState() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("workflow.enabled=true", "generator.enabled=true")
                .run(context -> {
                    var service = context.getBean(SysMenuService.class);
                    assertThat(service.findMenuByRoleId(1L)).hasSize(4);

                    context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("generator-toggle", Map.of("generator.enabled", "false")));
                    assertThat(service.findMenuByRoleId(1L))
                            .noneMatch(menu -> isGeneratorMenu(menu));
                });
    }

    private static boolean isGeneratorMenu(SysMenu menu) {
        String path = menu.getPath() == null ? "" : menu.getPath();
        String permission = menu.getPermission() == null ? "" : menu.getPermission();
        return path.equals("/gen") || path.startsWith("/gen/") || permission.startsWith("codegen_");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(SysMenuServiceImpl.class)
    static class Config {
        @Bean
        SysMenuMapper menuMapper() {
            var mapper = mock(SysMenuMapper.class);
            var core = menu(1L, "/demo/task/index", "demo_task_view");
            var generatorRoot = menu(2L, "/gen", null);
            var generatorPage = menu(3L, "/gen/table/index", null);
            var generatorButton = menu(4L, null, "codegen_table_view");
            when(mapper.listMenusByRoleId(1L)).thenReturn(List.of(core, generatorRoot, generatorPage, generatorButton));
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
