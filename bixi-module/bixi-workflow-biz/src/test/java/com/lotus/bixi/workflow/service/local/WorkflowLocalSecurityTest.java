package com.lotus.bixi.workflow.service.local;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.aspect.SysLogAspect;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogEvent;
import com.lotus.bixi.common.log.event.SysLogEventSource;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import com.lotus.bixi.workflow.service.WfTaskService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class WorkflowLocalSecurityTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(SecuredAdapter.class)
            .withBean(ProcessInstanceService.class, () -> mock(ProcessInstanceService.class))
            .withBean(WfTaskService.class, () -> mock(WfTaskService.class))
            .withPropertyValues("workflow.enabled=true", "bixi.deployment.mode=single",
                    "spring.application.name=workflow-local-test");

    @BeforeEach
    void bindRequestForAudit() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/business/approve")));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void missingPermissionsDenyEveryContractMethodBeforeTheBusinessCall() {
        WorkflowLocalAdapterTest.authenticate(7L);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WorkflowService adapter = context.getBean(WorkflowService.class);
            List<Runnable> calls = List.of(() -> adapter.startProcess(new ProcessStartDTO()),
                    () -> adapter.getProcessInstance("process-7"), () -> adapter.getApprovalHistory("process-7"),
                    () -> adapter.getTodoTasks(1, 10), () -> adapter.getDoneTasks(1, 10),
                    () -> adapter.completeTask(new TaskCompleteDTO()), () -> adapter.rejectTask(new TaskRejectDTO()),
                    () -> adapter.transferTask(new TaskTransferDTO()));
            calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class));
            verifyNoInteractions(context.getBean(ProcessInstanceService.class), context.getBean(WfTaskService.class));
        });
    }

    @Test
    void readPermissionsAllowProcessAndCurrentUserTaskQueries() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_process_view", "workflow_task_view");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var tasks = context.getBean(WfTaskService.class);
            when(tasks.todoPage(any(Page.class), eq(7L))).thenReturn(new Page<>(1, 10));
            when(tasks.donePage(any(Page.class), eq(7L))).thenReturn(new Page<>(1, 10));
            var adapter = context.getBean(WorkflowService.class);

            assertThat(adapter.getProcessInstance("process-7").getCode()).isZero();
            assertThat(adapter.getApprovalHistory("process-7").getCode()).isZero();
            assertThat(adapter.getTodoTasks(1, 10).getCode()).isZero();
            assertThat(adapter.getDoneTasks(1, 10).getCode()).isZero();
            verify(context.getBean(ProcessInstanceService.class)).getById("process-7");
            verify(context.getBean(ProcessInstanceService.class)).getApprovalHistory("process-7");
        });
    }

    @Test
    void authorizedWritesPublishRealAuditEvents() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_process_add", "workflow_task_edit");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/business/approve");
        request.addParameter("processKey", "approval");
        request.addParameter("password", "test-secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(BixiLogProperties.class).setExcludeFields(List.of("password"));
            List<SysLog> logs = new ArrayList<>();
            context.addApplicationListener(event -> {
                if (event instanceof SysLogEvent sysLogEvent) {
                    logs.add((SysLog) sysLogEvent.getSource());
                }
            });
            WorkflowService adapter = context.getBean(WorkflowService.class);
            ProcessStartDTO start = new ProcessStartDTO();
        start.setRequestId(java.util.UUID.randomUUID().toString());
            start.setProcessKey("approval");
            TaskCompleteDTO complete = new TaskCompleteDTO();
            complete.setRequestId(java.util.UUID.randomUUID().toString());
            complete.setTaskId("task-7");
            TaskRejectDTO reject = new TaskRejectDTO();
            reject.setRequestId(java.util.UUID.randomUUID().toString());
            reject.setTaskId("task-7");
            reject.setRejectReason("资料不完整");
            TaskTransferDTO transfer = new TaskTransferDTO();
            transfer.setRequestId(java.util.UUID.randomUUID().toString());
            transfer.setTaskId("task-7");
            transfer.setTransferUserId(9L);
            adapter.startProcess(start);
            adapter.completeTask(complete);
            adapter.rejectTask(reject);
            adapter.transferTask(transfer);

            assertThat(logs).extracting(SysLog::getTitle).containsExactly("发起流程", "完成任务", "驳回任务", "转办任务");
            assertThat(logs).allSatisfy(log -> {
                assertThat(log.getMethod()).isEqualTo("POST");
                assertThat(log.getRequestUri()).isEqualTo("/business/approve");
                assertThat(log.getParams()).contains("processKey", "approval").doesNotContain("password", "test-secret");
            });
            assertThat(request.getParameter("password")).isEqualTo("test-secret");
        });
    }

    @Test
    void authorizedLocalWritesRunAndPublishAuditEventsWithoutAServletRequest() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_process_add", "workflow_task_edit");
        RequestContextHolder.resetRequestAttributes();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            List<SysLogEventSource> logs = new ArrayList<>();
            context.addApplicationListener(event -> {
                if (event instanceof SysLogEvent sysLogEvent) {
                    logs.add((SysLogEventSource) sysLogEvent.getSource());
                }
            });
            WorkflowService adapter = context.getBean(WorkflowService.class);
            ProcessStartDTO start = new ProcessStartDTO();
        start.setRequestId(java.util.UUID.randomUUID().toString());
            start.setProcessKey("approval");
            TaskCompleteDTO complete = new TaskCompleteDTO();
            complete.setRequestId(java.util.UUID.randomUUID().toString());
            complete.setTaskId("task-7");

            assertThatCode(() -> {
                assertThat(adapter.startProcess(start).getCode()).isZero();
                assertThat(adapter.completeTask(complete).getCode()).isZero();
            }).doesNotThrowAnyException();

            verify(context.getBean(ProcessInstanceService.class)).start(start);
            verify(context.getBean(WfTaskService.class)).complete(complete);
            assertThat(logs).extracting(SysLog::getTitle).containsExactly("发起流程", "完成任务");
            assertThat(logs).allSatisfy(log -> {
                assertThat(log.getMethod()).isEqualTo("LOCAL");
                assertThat(log.getServiceId()).isEqualTo("workflow-local-test");
                assertThat(log.getRemoteAddr()).isNull();
                assertThat(log.getTime()).isNotNegative();
            });
            assertThat(logs.get(0).getRequestUri()).endsWith("LocalWorkflowService#startProcess");
            assertThat(logs.get(1).getRequestUri()).endsWith("LocalWorkflowService#completeTask");
            assertThat((Object[]) logs.get(0).getBody()).containsExactly(start);
            assertThat((Object[]) logs.get(1).getBody()).containsExactly(complete);
        });
    }

    @Test
    void invalidStartIsRejectedBeforeTheBusinessCall() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_process_add");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ProcessStartDTO start = new ProcessStartDTO();
        start.setRequestId(java.util.UUID.randomUUID().toString());
            start.setProcessKey(" ");

            assertThatThrownBy(() -> context.getBean(WorkflowService.class).startProcess(start))
                    .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("processKey");
            verifyNoInteractions(context.getBean(ProcessInstanceService.class));
        });
    }

    @Test
    void invalidTaskWritesAreRejectedBeforeTheBusinessCall() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_task_edit");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WorkflowService adapter = context.getBean(WorkflowService.class);
            TaskCompleteDTO complete = new TaskCompleteDTO();
            complete.setTaskId(" ");
            TaskRejectDTO reject = new TaskRejectDTO();
            reject.setTaskId("task-7");
            TaskTransferDTO transfer = new TaskTransferDTO();
            transfer.setTaskId("task-7");

            assertThatThrownBy(() -> adapter.completeTask(complete))
                    .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("taskId");
            assertThatThrownBy(() -> adapter.rejectTask(reject))
                    .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("rejectReason");
            assertThatThrownBy(() -> adapter.transferTask(transfer))
                    .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("transferUserId");
            verifyNoInteractions(context.getBean(WfTaskService.class));
        });
    }

    @Test
    void nullRequestsAreRejectedBeforeTheBusinessCall() {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_process_add", "workflow_task_edit");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WorkflowService adapter = context.getBean(WorkflowService.class);
            List<Runnable> calls = List.of(() -> adapter.startProcess(null), () -> adapter.completeTask(null),
                    () -> adapter.rejectTask(null), () -> adapter.transferTask(null));

            calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(ConstraintViolationException.class));
            verifyNoInteractions(context.getBean(ProcessInstanceService.class), context.getBean(WfTaskService.class));
        });
    }

    @ParameterizedTest
    @CsvSource({"0,10", "-1,10", "1,0", "1,-1"})
    void invalidPaginationIsRejectedBeforeTheBusinessCall(long current, long size) {
        WorkflowLocalAdapterTest.authenticate(7L, "workflow_task_view");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WorkflowService adapter = context.getBean(WorkflowService.class);
            WfTaskService tasks = context.getBean(WfTaskService.class);
            when(tasks.todoPage(any(Page.class), eq(7L))).thenReturn(new Page<>(1, 10));
            when(tasks.donePage(any(Page.class), eq(7L))).thenReturn(new Page<>(1, 10));

            assertThatThrownBy(() -> adapter.getTodoTasks(current, size))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> adapter.getDoneTasks(current, size))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(context.getBean(WfTaskService.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @EnableAspectJAutoProxy
    @Import(LocalWorkflowService.class)
    static class SecuredAdapter {
        @Bean("pms")
        PermissionService permissions() {
            return new PermissionService();
        }

        @Bean
        static PrePostTemplateDefaults permissionTemplates() {
            return new PrePostTemplateDefaults();
        }

        @Bean
        SysLogAspect sysLogAspect() {
            return new SysLogAspect();
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
