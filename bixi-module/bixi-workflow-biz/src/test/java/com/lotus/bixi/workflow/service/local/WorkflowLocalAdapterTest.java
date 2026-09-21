package com.lotus.bixi.workflow.service.local;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import com.lotus.bixi.workflow.service.WfTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowLocalAdapterTest {

    private final ProcessInstanceService processes = mock(ProcessInstanceService.class);
    private final WfTaskService tasks = mock(WfTaskService.class);
    private final LocalWorkflowService local = new LocalWorkflowService(processes, tasks);

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processOperationsPreserveDtosAndResults() {
        ProcessStartDTO start = new ProcessStartDTO();
        start.setRequestId(java.util.UUID.randomUUID().toString());
        start.setProcessKey("approval");
        ProcessInstanceVO instance = new ProcessInstanceVO();
        instance.setProcessInstanceId("process-7");
        ApprovalRecordVO record = new ApprovalRecordVO();
        record.setApprovalType("approve");
        when(processes.start(start)).thenReturn(instance);
        when(processes.getById("process-7")).thenReturn(instance);
        when(processes.getApprovalHistory("process-7")).thenReturn(List.of(record));

        assertThat(local.startProcess(start).getData()).isSameAs(instance);
        assertThat(local.getProcessInstance("process-7").getData()).isSameAs(instance);
        assertThat(local.getApprovalHistory("process-7").getData()).containsExactly(record);
        verify(processes).start(start);
    }

    @Test
    void idempotencyConflictAndCommandLookupPreserveTheirContract() {
        var dto = new ProcessStartDTO(); dto.setRequestId(java.util.UUID.randomUUID().toString());
        var conflict = new com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException(dto.getRequestId());
        when(processes.start(dto)).thenThrow(conflict);
        assertThatThrownBy(() -> local.startProcess(dto)).isSameAs(conflict);
        var saved = new com.lotus.bixi.workflow.api.vo.WorkflowCommandVO(dto.getRequestId(), "START", "approval",
                "process-7", "SUCCESS", java.time.Instant.now(), null);
        when(processes.getCommand(dto.getRequestId())).thenReturn(saved);
        assertThat(local.getCommand(dto.getRequestId()).getData()).isSameAs(saved);
        var complete = new TaskCompleteDTO();
        var operationConflict = new com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException(dto.getRequestId());
        doThrow(operationConflict).when(tasks).complete(complete);
        assertThatThrownBy(() -> local.completeTask(complete)).isSameAs(operationConflict);
    }

    @Test
    void taskWritesReturnSuccessfulVoidResults() {
        TaskCompleteDTO complete = new TaskCompleteDTO();
        TaskRejectDTO reject = new TaskRejectDTO();
        TaskTransferDTO transfer = new TaskTransferDTO();

        assertThat(local.completeTask(complete).getCode()).isZero();
        assertThat(local.rejectTask(reject).getData()).isNull();
        assertThat(local.transferTask(transfer).getData()).isNull();
        verify(tasks).complete(complete);
        verify(tasks).reject(reject);
        verify(tasks).transfer(transfer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void paginationUsesTheCurrentIdentityAndConvertsAnyIPageToConcretePage() {
        authenticate(7L, "workflow_task_view");
        TaskVO task = new TaskVO();
        task.setTaskId("task-7");
        IPage<TaskVO> results = mock(IPage.class);
        when(results.getCurrent()).thenReturn(2L);
        when(results.getSize()).thenReturn(5L);
        when(results.getTotal()).thenReturn(13L);
        when(results.getRecords()).thenReturn(List.of(task));
        when(tasks.todoPage(any(Page.class), eq(7L))).thenReturn(results);
        when(tasks.donePage(any(Page.class), eq(7L))).thenReturn(results);

        var todo = local.getTodoTasks(2, 5).getData();
        var done = local.getDoneTasks(2, 5).getData();

        for (Page<TaskVO> page : List.of(todo, done)) {
            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(5);
            assertThat(page.getTotal()).isEqualTo(13);
            assertThat(page.getRecords()).containsExactly(task);
        }
        ArgumentCaptor<Page> requestedPage = ArgumentCaptor.forClass(Page.class);
        verify(tasks).todoPage(requestedPage.capture(), eq(7L));
        assertThat(requestedPage.getValue().getCurrent()).isEqualTo(2);
        assertThat(requestedPage.getValue().getSize()).isEqualTo(5);
        verify(tasks).donePage(any(Page.class), eq(7L));
    }

    @Test
    void businessFailuresPropagateWithoutSuccessfulWrapping() {
        TaskCompleteDTO dto = new TaskCompleteDTO();
        doThrow(new IllegalArgumentException("Task belongs to another user")).when(tasks).complete(dto);

        assertThatThrownBy(() -> local.completeTask(dto)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task belongs to another user");
    }

    static void authenticate(Long id, String... permissions) {
        var authorities = AuthorityUtils.createAuthorityList(permissions);
        var user = new BixiUser(id, 1L, "user-" + id, "password", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, "", authorities));
    }
}
