package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import com.lotus.bixi.workflow.event.WorkflowTerminalEventSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Publishes terminal workflow outcomes through the durable event path when it is enabled.
 * The compatibility notifier remains the fallback for the legacy synchronous mode.
 */
@Component
@ConditionalOnWorkflowEnabled
public final class WorkflowTerminalEventPublisher {
    private final ObjectProvider<WorkflowTerminalEventSink> recorder;
    private final WorkflowResultNotifier compatibility;

    public WorkflowTerminalEventPublisher(ObjectProvider<WorkflowTerminalEventSink> recorder,
            WorkflowResultNotifier compatibility) {
        this.recorder = recorder;
        this.compatibility = compatibility;
    }

    public void publish(WfProcessInstance instance, WorkflowOutcome outcome,
            long actorId, String actorName, String causationId) {
        if (instance == null || instance.getProcessInstanceId() == null) {
            throw new IllegalArgumentException("流程终态缺少流程实例");
        }
        WorkflowTerminalEventSink durable = recorder.getIfAvailable();
        if (durable == null || !hasDurableAssociation(instance) || outcome == null) {
            compatibility.afterCommit(instance.getProcessInstanceId());
            return;
        }

        String requestHash = instance.getStartRequestHash();
        if (requestHash == null) {
            requestHash = sha256(instance.getStartRequestId());
        }
        String correlationId = instance.getStartRequestId();
        String safeCausationId = isUuid(causationId) ? causationId : null;
        durable.recordCompleted(instance.getProcessInstanceId(), instance.getBusinessId(),
                instance.getBusinessKey(), instance.getBusinessRound(), instance.getStartRequestId(),
                actorId, actorName, requestHash, outcome,
                instance.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                correlationId, safeCausationId);
    }

    private static boolean hasDurableAssociation(WfProcessInstance instance) {
        return instance.getBusinessId() != null && instance.getBusinessKey() != null
                && instance.getBusinessRound() != null && instance.getStartRequestId() != null
                && instance.getEndTime() != null;
    }

    private static boolean isUuid(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
