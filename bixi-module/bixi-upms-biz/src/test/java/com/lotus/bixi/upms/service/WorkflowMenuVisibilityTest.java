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
import static org.mockito.Mockito.*;

class WorkflowMenuVisibilityTest {
    @Test
    void disablingAndReenablingDoesNotReuseMenusCachedForTheOtherState() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("workflow.enabled=true").run(context -> {
                    var mapper = context.getBean(SysMenuMapper.class);
                    var core = menu(1L, "/demo/task/index", "demo_task_view");
                    var leave = menu(2L, "/demo/leave/index", null);
                    var tasks = menu(3L, "/workflow/task/todo", "workflow_task_view");
                    var button = menu(4L, null, "demo_leave_edit");
                    when(mapper.listMenusByRoleId(1L)).thenReturn(List.of(core, leave, tasks, button));
                    var service = context.getBean(SysMenuService.class);
                    assertThat(service.findMenuByRoleId(1L)).hasSize(4);
                    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("toggle", Map.of("workflow.enabled", "false")));
                    assertThat(service.findMenuByRoleId(1L)).containsExactly(core);
                    context.getEnvironment().getPropertySources().remove("toggle");
                    assertThat(service.findMenuByRoleId(1L)).hasSize(4);
                });
    }

    private static SysMenu menu(long id, String path, String permission) {
        var menu = new SysMenu(); menu.setId(id); menu.setPath(path); menu.setPermission(permission); return menu;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(SysMenuServiceImpl.class)
    static class Config {
        @Bean SysMenuMapper menuMapper() { return mock(SysMenuMapper.class); }
        @Bean SysRoleMenuMapper roleMenuMapper() { return mock(SysRoleMenuMapper.class); }
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(CacheConstants.MENU_DETAILS); }
    }
}
