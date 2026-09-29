package com.lotus.bixi.workflow.event;

import java.time.Instant;
import java.util.List;

/** Optional durable sink used by task listeners when reliable delivery is enabled. */
public interface WorkflowTaskNotificationSink {

    void record(Context context);

    record Context(
            String processDefinitionId,
            String processInstanceId,
            String processKey,
            String taskId,
            String taskName,
            List<Long> recipientUserIds,
            List<Long> recipientRoleIds,
            String tenantScope,
            String businessKey,
            String commandId,
            String operationId,
            Instant occurredAt) {
        public Context {
            recipientUserIds = List.copyOf(recipientUserIds == null ? List.of() : recipientUserIds);
            recipientRoleIds = List.copyOf(recipientRoleIds == null ? List.of() : recipientRoleIds);
        }
    }
}
