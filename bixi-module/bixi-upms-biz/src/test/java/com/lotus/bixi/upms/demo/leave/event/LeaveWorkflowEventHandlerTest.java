package com.lotus.bixi.upms.demo.leave.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowCompleted;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveWorkflowEventHandlerTest {
    private final LeaveRequestMapper leaves = mock(LeaveRequestMapper.class);
    private final WorkflowEventCodec codec = new WorkflowEventCodec();
    private final LeaveWorkflowEventHandler handler = new LeaveWorkflowEventHandler(leaves, codec);

    @BeforeEach
    void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "leave-test"), LeaveRequest.class);
    }

    @Test
    void completionByTheWorkflowApproverBindsAndCompletesTheCurrentLeave() {
        LeaveRequest leave = leave("SUBMITTING");
        when(leaves.selectById(leave.getId())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.get()).isEqualTo(1L);
            return leave;
        });
        when(leaves.update(any(), any())).thenReturn(1);

        WorkflowEvent event = new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_COMPLETED,
                1, "workflow", "upms", "default", "process-7", "demo_leave_approval",
                "demo_leave_request", leave.getId(), leave.getBusinessKey(), 1, UUID.randomUUID().toString(), 2,
                Instant.parse("2026-09-21T12:00:00Z"), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                new WorkflowActorSnapshot(88L, "approver", "default", "upms", Instant.now()),
                new WorkflowCompleted("a".repeat(64), WorkflowOutcome.APPROVED,
                        Instant.parse("2026-09-21T12:00:00Z")));

        DurableMessage message = DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(),
                1, new String(codec.encode(event), java.nio.charset.StandardCharsets.UTF_8));

        assertThat(handler.handle(message)).isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void futureRoundIsRetryableInsteadOfBeingAcknowledgedAsAnOldEvent() {
        LeaveRequest leave = leave("IN_REVIEW");
        when(leaves.selectById(leave.getId())).thenReturn(leave);
        WorkflowEvent event = completion(leave, 2, UUID.randomUUID().toString());
        DurableMessage message = DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(),
                1, new String(codec.encode(event), java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.class)
                .extracting("kind")
                .isEqualTo(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.Kind.RETRYABLE);
    }

    @Test
    void completionMustMatchThePersistedStartCommand() {
        LeaveRequest leave = leave("IN_REVIEW");
        leave.setProcessInstanceId("process-7");
        leave.setStartCommandId("11111111-1111-1111-1111-111111111111");
        when(leaves.selectById(leave.getId())).thenReturn(leave);
        WorkflowEvent event = completion(leave, 1, "22222222-2222-2222-2222-222222222222");
        DurableMessage message = DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(),
                1, new String(codec.encode(event), java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.class)
                .extracting("kind")
                .isEqualTo(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.Kind.PERMANENT);
    }

    private WorkflowEvent completion(LeaveRequest leave, int round, String commandId) {
        return new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_COMPLETED,
                1, "workflow", "upms", "default", "process-7", "demo_leave_approval",
                "demo_leave_request", leave.getId(), leave.getBusinessKey(), round, commandId, 2,
                Instant.parse("2026-09-21T12:00:00Z"), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                new WorkflowActorSnapshot(88L, "approver", "default", "upms", Instant.now()),
                new WorkflowCompleted("a".repeat(64), WorkflowOutcome.APPROVED,
                        Instant.parse("2026-09-21T12:00:00Z")));
    }

    private static LeaveRequest leave(String status) {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(7L);
        leave.setApplicantId(42L);
        leave.setApproverId(88L);
        leave.setLeaveStatus(status);
        leave.setRound(1);
        leave.setBusinessKey("demo_leave:7:1");
        leave.setStartDate(LocalDate.of(2026, 9, 21));
        leave.setEndDate(LocalDate.of(2026, 9, 22));
        leave.setReason("test");
        leave.setStartRequestHash("a".repeat(64));
        return leave;
    }
}
