package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import com.lotus.bixi.workflow.event.WorkflowTerminalEventSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowTerminalEventPublisherTest {

    @Test
    void reliableModePersistsTerminalEventBeforeCommit() {
        WorkflowTerminalEventSink recorder = mock(WorkflowTerminalEventSink.class);
        WorkflowResultNotifier compatibility = mock(WorkflowResultNotifier.class);
        ObjectProvider<WorkflowTerminalEventSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);

        WfProcessInstance instance = instance();
        String causationId = "22222222-2222-2222-2222-222222222222";
        new WorkflowTerminalEventPublisher(provider, compatibility)
                .publish(instance, WorkflowOutcome.REJECTED, 22L, "reviewer", causationId);

        verify(recorder).recordCompleted(
                eq("process-1"), eq(7L), eq("leave:7:2"), eq(2),
                eq("11111111-1111-1111-1111-111111111111"), eq(22L), eq("reviewer"),
                eq("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                eq(WorkflowOutcome.REJECTED), any(), eq("11111111-1111-1111-1111-111111111111"),
                eq(causationId));
        verifyNoInteractions(compatibility);
    }

    @Test
    void legacyModeUsesCommittedCompatibilityNotification() {
        ObjectProvider<WorkflowTerminalEventSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        WorkflowResultNotifier compatibility = mock(WorkflowResultNotifier.class);

        new WorkflowTerminalEventPublisher(provider, compatibility)
                .publish(instance(), WorkflowOutcome.CANCELED, 11L, "applicant", "cancel-1");

        verify(compatibility).afterCommit("process-1");
    }

    private static WfProcessInstance instance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setProcessInstanceId("process-1");
        instance.setBusinessId(7L);
        instance.setBusinessKey("leave:7:2");
        instance.setBusinessRound(2);
        instance.setStartRequestId("11111111-1111-1111-1111-111111111111");
        instance.setStartRequestHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        instance.setStartUserId(11L);
        instance.setStartUserName("applicant");
        instance.setEndTime(LocalDateTime.of(2026, 9, 22, 10, 0));
        return instance;
    }
}
