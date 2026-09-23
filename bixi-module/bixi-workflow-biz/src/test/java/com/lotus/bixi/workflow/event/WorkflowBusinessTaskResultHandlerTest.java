package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowBusinessTaskResultHandlerTest {
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROCESS_ID = "process-7";
    private static final String HASH = "a".repeat(64);

    private RuntimeService runtime;
    private ExecutionQuery executions;
    private Execution execution;
    private WorkflowEventCodec codec;
    private WorkflowBusinessTaskResultHandler handler;

    @BeforeEach
    void setUp() {
        runtime = mock(RuntimeService.class);
        executions = mock(ExecutionQuery.class);
        execution = mock(Execution.class);
        codec = new WorkflowEventCodec();
        handler = new WorkflowBusinessTaskResultHandler(runtime, codec);
        when(runtime.createExecutionQuery()).thenReturn(executions);
        when(executions.processInstanceId(PROCESS_ID)).thenReturn(executions);
        when(executions.activityId("waitBusinessResult")).thenReturn(executions);
        when(execution.getId()).thenReturn("execution-7");
        when(runtime.getVariable("execution-7", "businessOperationId")).thenReturn(OPERATION_ID);
        when(runtime.getVariable("execution-7", "startRequestHash")).thenReturn(HASH);
    }

    @Test
    void resultSetsControlledVariablesAndTriggersReceiveTask() {
        when(executions.list()).thenReturn(List.of(execution));

        assertThat(handler.handle(message(result(true)))).isEqualTo(DurableMessageHandler.Result.PROCESSED);

        verify(runtime).setVariables(eq("execution-7"), any());
        verify(runtime).trigger("execution-7");
    }

    @Test
    void duplicateResultAfterReceiveWasAlreadyTriggeredIsIgnored() {
        when(executions.list()).thenReturn(List.of());
        when(runtime.getVariable(PROCESS_ID, "businessTaskResultOperationId")).thenReturn(OPERATION_ID);

        assertThat(handler.handle(message(result(true)))).isEqualTo(DurableMessageHandler.Result.IGNORED);
    }

    private WorkflowEvent result(boolean success) {
        return new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT,
                1, "upms", "workflow", "default", PROCESS_ID, "demo_leave_approval", "demo_leave_request",
                7L, "leave:7:1", 1, UUID.randomUUID().toString(), 3, Instant.now(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), new WorkflowActorSnapshot(42L, "applicant", "default", "upms", Instant.now()),
                success ? new WorkflowBusinessTaskResult(HASH, OPERATION_ID, true, "booking-ref", null, Instant.now())
                        : new WorkflowBusinessTaskResult(HASH, OPERATION_ID, false, null, "BOOKING_CONFLICT", Instant.now()));
    }

    private DurableMessage message(WorkflowEvent event) {
        return DurableMessage.create("upms", "workflow", event.eventId(), event.type().name(), 1,
                new String(codec.encode(event), java.nio.charset.StandardCharsets.UTF_8));
    }
}
