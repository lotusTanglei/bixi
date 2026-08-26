package com.lotus.bixi.upms.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.demo.dto.DemoTaskQuery;
import com.lotus.bixi.upms.demo.entity.DemoTask;
import com.lotus.bixi.upms.demo.service.DemoTaskService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoTaskControllerTest {

    private final DemoTaskService taskService = mock(DemoTaskService.class);

    private final DemoTaskController controller = new DemoTaskController(taskService);

    @Test
    void shouldDelegatePageQuery() {
        Page<DemoTask> requestedPage = new Page<>(2, 10);
        DemoTaskQuery query = new DemoTaskQuery();
        query.setAssignee("Alice");
        Page<DemoTask> resultPage = new Page<>(2, 10, 21);
        when(taskService.pageTasks(requestedPage, query)).thenReturn(resultPage);

        R<Page<DemoTask>> result = controller.page(requestedPage, query);

        assertThat(result.getData()).isSameAs(resultPage);
        verify(taskService).pageTasks(requestedPage, query);
    }

    @Test
    void shouldDelegateAllWriteOperations() {
        DemoTask task = new DemoTask();
        task.setId(42L);
        List<Long> ids = List.of(42L);
        when(taskService.save(task)).thenReturn(true);
        when(taskService.updateById(task)).thenReturn(true);
        when(taskService.removeBatchByIds(ids)).thenReturn(true);

        assertThat(controller.save(task).getData()).isTrue();
        assertThat(controller.update(task).getData()).isTrue();
        assertThat(controller.delete(ids).getData()).isTrue();
    }

    @Test
    void shouldExposePermissionAndAuditContracts() throws Exception {
        assertPermission("page", new Class<?>[]{Page.class, DemoTaskQuery.class}, "demo_task_view", false);
        assertPermission("details", new Class<?>[]{Long.class}, "demo_task_view", false);
        assertPermission("save", new Class<?>[]{DemoTask.class}, "demo_task_add", true);
        assertPermission("update", new Class<?>[]{DemoTask.class}, "demo_task_edit", true);
        assertPermission("delete", new Class<?>[]{List.class}, "demo_task_del", true);
    }

    private void assertPermission(String name, Class<?>[] parameterTypes, String permission, boolean audited)
            throws Exception {
        Method method = DemoTaskController.class.getMethod(name, parameterTypes);
        assertThat(method.getAnnotation(HasPermission.class).value()).containsExactly(permission);
        assertThat(method.isAnnotationPresent(SysLog.class)).isEqualTo(audited);
    }

}
