package com.lotus.bixi.upms.demo.leave.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.upms.demo.leave.entity.LeaveBooking;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.upms.demo.leave.service.LeaveBookingService;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveBusinessTaskEventHandlerTest {
    private static final long LEAVE_ID = 7L;
    private static final String HASH = "a".repeat(64);
    private static final String PROCESS_ID = "process-7";

    @Mock LeaveRequestMapper leaves;
    @Mock LeaveBookingMapper bookings;
    @Mock LeaveBusinessTaskEventHandler.Outbox outbox;

    private WorkflowEventCodec codec;
    private LeaveBusinessTaskEventHandler handler;

    @BeforeEach
    void setUp() {
        codec = new WorkflowEventCodec();
        handler = new LeaveBusinessTaskEventHandler(leaves, new LeaveBookingService(bookings), outbox, codec);
    }

    @Test
    void businessTaskRequestBooksAndPublishesDurableResult() {
        LeaveRequest leave = leave();
        when(leaves.selectByIdForUpdate(LEAVE_ID)).thenReturn(leave);
        when(bookings.selectByOperationIdForUpdate(any())).thenReturn(null);
        when(bookings.selectByLeaveRoundForUpdate(LEAVE_ID, 1)).thenReturn(null);
        when(bookings.insert(any(LeaveBooking.class))).thenReturn(1);

        WorkflowEvent event = requestEvent();
        DurableMessage message = message(event);

        assertThat(handler.handle(message)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
        ArgumentCaptor<DurableMessage> sent = ArgumentCaptor.forClass(DurableMessage.class);
        verify(outbox).enqueue(sent.capture(), any(), any(), any());
        DurableMessage result = sent.getValue();
        assertThat(result.sourceOwner()).isEqualTo("upms");
        assertThat(result.targetOwner()).isEqualTo("workflow");
        assertThat(result.type()).isEqualTo(WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT.name());
        assertThat(codec.decode(result.payloadJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .extracting(WorkflowEvent::type)
                .isEqualTo(WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT);
    }

    @Test
    void requestWithWrongProcessBindingIsPermanent() {
        LeaveRequest leave = leave();
        leave.setProcessInstanceId("different-process");
        when(leaves.selectByIdForUpdate(LEAVE_ID)).thenReturn(leave);

        assertThatThrownBy(() -> handler.handle(message(requestEvent())))
                .isInstanceOf(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.class)
                .extracting("kind")
                .isEqualTo(com.lotus.bixi.common.mq.reliable.InboxDeliveryException.Kind.PERMANENT);
    }

    private WorkflowEvent requestEvent() {
        return new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_BUSINESS_TASK_REQUESTED,
                1, "workflow", "upms", "default", PROCESS_ID, "demo_leave_approval",
                "demo_leave_request", LEAVE_ID, "leave:7:1", 1, UUID.randomUUID().toString(), 2,
                Instant.parse("2026-09-22T00:00:00Z"), UUID.randomUUID().toString(), null,
                new WorkflowActorSnapshot(42L, "applicant", "default", "upms", Instant.now()),
                new WorkflowBusinessTaskRequested(HASH, UUID.randomUUID().toString(), "execution-7",
                        "bookLeave", 1, Instant.parse("2026-09-22T00:05:00Z")));
    }

    private DurableMessage message(WorkflowEvent event) {
        return DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(), 1,
                new String(codec.encode(event), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static LeaveRequest leave() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(LEAVE_ID);
        leave.setApplicantId(42L);
        leave.setApproverId(88L);
        leave.setRound(1);
        leave.setBusinessKey("leave:7:1");
        leave.setProcessInstanceId(PROCESS_ID);
        leave.setStartRequestHash(HASH);
        leave.setLeaveStatus("IN_REVIEW");
        leave.setStartDate(LocalDate.of(2026, 9, 22));
        leave.setEndDate(LocalDate.of(2026, 9, 23));
        leave.setReason("test");
        return leave;
    }
}
