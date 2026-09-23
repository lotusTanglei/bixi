package com.lotus.bixi.upms.demo.leave.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.api.event.WorkflowStartRequested;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Writes the trusted leave start command to UPMS outbox inside the submit transaction. */
@Component
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
public final class LeaveWorkflowEventPublisher {
    private final JdbcOutboxStore outbox;
    private final WorkflowEventCodec codec;

    public LeaveWorkflowEventPublisher(@Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public Published publishStart(LeaveRequest leave, BixiUser actor) {
        String commandId = UUID.nameUUIDFromBytes(("leave:start:" + leave.getId() + ":" + leave.getRound())
                .getBytes(StandardCharsets.UTF_8)).toString();
        String requestHash = requestHash(leave);
        WorkflowEvent event = new WorkflowEvent(
                UUID.nameUUIDFromBytes(("leave:event:" + leave.getId() + ":" + leave.getRound() + ":start")
                        .getBytes(StandardCharsets.UTF_8)).toString(),
                WorkflowEventType.WORKFLOW_START_REQUESTED, 1, "upms", "workflow", "default", null,
                "demo_leave_approval", "demo_leave_request", leave.getId(), leave.getBusinessKey(),
                leave.getRound(), commandId, 0, Instant.now(), commandId, null,
                new WorkflowActorSnapshot(actor.getId(), actor.getUsername(), "default", "upms", Instant.now()),
                new WorkflowStartRequested("请假申请 " + leave.getStartDate() + " 至 " + leave.getEndDate(),
                        leave.getApproverId(), requestHash));
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        outbox.enqueue(DurableMessage.create("upms", "workflow", event.eventId(), event.type().name(), 1, payload),
                "leave:" + leave.getId() + ":" + leave.getRound() + ":start", leave.getBusinessKey(), 0L);
        return new Published(commandId, requestHash);
    }

    private static String requestHash(LeaveRequest leave) {
        String material = String.join("|", String.valueOf(leave.getId()), String.valueOf(leave.getRound()),
                String.valueOf(leave.getApplicantId()), String.valueOf(leave.getApproverId()),
                String.valueOf(leave.getStartDate()), String.valueOf(leave.getEndDate()), leave.getReason());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Published(String commandId, String requestHash) { }
}
