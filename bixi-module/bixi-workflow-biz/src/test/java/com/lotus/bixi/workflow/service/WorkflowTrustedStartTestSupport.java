package com.lotus.bixi.workflow.service;

import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.api.event.WorkflowStartRequested;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;

import java.time.Instant;
import java.util.UUID;

final class WorkflowTrustedStartTestSupport {
    private WorkflowTrustedStartTestSupport() {
    }

    static ProcessInstanceVO start(TrustedProcessStarter starter, String title, long businessId, int round) {
        String commandId = UUID.randomUUID().toString();
        String businessKey = "demo_leave:" + businessId + ":" + round + ":" + UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowEvent event = new WorkflowEvent(UUID.randomUUID().toString(),
                WorkflowEventType.WORKFLOW_START_REQUESTED, 1, "upms", "workflow", "1", null,
                "demo_leave_approval", "demo_leave_request", businessId, businessKey, round, commandId, 0,
                now, commandId, null, new WorkflowActorSnapshot(11L, "user-11", "1", "upms", now),
                new WorkflowStartRequested(title, 22L, "a".repeat(64)));
        TrustedProcessStarter.StartResult result = starter.startTrusted(event);
        if (result.rejected() || result.process() == null) {
            throw new IllegalStateException("Trusted test start was rejected: " + result.rejectionCode());
        }
        return result.process();
    }
}
