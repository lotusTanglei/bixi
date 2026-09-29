package com.lotus.bixi.workflow.listener;

import com.lotus.bixi.workflow.event.WorkflowTaskNotificationSink;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.identitylink.api.IdentityLink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

class TaskCreateListenerTest {

    @Test
    void recordsAStableDurableNotificationForAnAssignedTask() {
        WorkflowTaskNotificationSink sink = mock(WorkflowTaskNotificationSink.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowTaskNotificationSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);
        DelegateTask task = task("22");

        new TaskCreateListener(provider).notify(task);

        ArgumentCaptor<WorkflowTaskNotificationSink.Context> captured =
                ArgumentCaptor.forClass(WorkflowTaskNotificationSink.Context.class);
        verify(sink).record(captured.capture());
        WorkflowTaskNotificationSink.Context context = captured.getValue();
        assertThat(context.tenantScope()).isEqualTo("42");
        assertThat(context.processKey()).isEqualTo("demo_leave_approval");
        assertThat(context.recipientUserIds()).containsExactly(22L);
        assertThat(context.recipientRoleIds()).isEmpty();
        assertThat(context.commandId()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(context.operationId()).matches("[0-9a-f-]{36}");
    }

    @Test
    void recordsCanonicalCandidateUsersAndRolesForAnUnassignedTask() {
        WorkflowTaskNotificationSink sink = mock(WorkflowTaskNotificationSink.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowTaskNotificationSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);

        DelegateTask task = task(null);
        IdentityLink user = mock(IdentityLink.class);
        when(user.getUserId()).thenReturn("33");
        IdentityLink role = mock(IdentityLink.class);
        when(role.getGroupId()).thenReturn("role:11");
        when(task.getCandidates()).thenReturn(Set.of(user, role));

        new TaskCreateListener(provider).notify(task);

        ArgumentCaptor<WorkflowTaskNotificationSink.Context> captured =
                ArgumentCaptor.forClass(WorkflowTaskNotificationSink.Context.class);
        verify(sink).record(captured.capture());
        assertThat(captured.getValue().recipientUserIds()).containsExactly(33L);
        assertThat(captured.getValue().recipientRoleIds()).containsExactly(11L);
    }

    @Test
    void unassignedTaskWithoutCandidatesDoesNotInventARecipient() {
        WorkflowTaskNotificationSink sink = mock(WorkflowTaskNotificationSink.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowTaskNotificationSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);

        DelegateTask task = task(null);
        when(task.getCandidates()).thenReturn(Set.of());
        new TaskCreateListener(provider).notify(task);

        verifyNoInteractions(sink);
    }

    @Test
    void embeddedAndGlobalCreateCallbacksRecordOnlyOnce() {
        WorkflowTaskNotificationSink sink = mock(WorkflowTaskNotificationSink.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowTaskNotificationSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);
        DelegateTask task = task("22");
        when(task.getTransientVariableLocal("bixiTaskNotificationRecorded"))
                .thenReturn(null, Boolean.TRUE);

        TaskCreateListener listener = new TaskCreateListener(provider);
        listener.notify(task);
        listener.notify(task);

        verify(sink, times(1)).record(org.mockito.ArgumentMatchers.any());
        verify(task).setTransientVariableLocal("bixiTaskNotificationRecorded", Boolean.TRUE);
    }

    private static DelegateTask task(String assignee) {
        DelegateTask task = mock(DelegateTask.class);
        when(task.getId()).thenReturn("task-9");
        when(task.getName()).thenReturn("请假审批");
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessInstanceId()).thenReturn("process-7");
        when(task.getProcessDefinitionId()).thenReturn("demo_leave_approval:3:deployment-1");
        when(task.getVariable("tenantScope")).thenReturn("42");
        when(task.getVariable("businessKey")).thenReturn("leave:7:1");
        when(task.getVariable("startRequestId"))
                .thenReturn("22222222-2222-2222-2222-222222222222");
        return task;
    }
}
