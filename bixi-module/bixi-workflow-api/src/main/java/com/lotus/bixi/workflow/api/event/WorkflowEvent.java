package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

/** Immutable stage 2B wire contract; transport and authorization belong to the adapters. */
public record WorkflowEvent(String eventId, WorkflowEventType type, int schemaVersion,
                            String sourceOwner, String targetOwner, String tenantScope,
                            String processInstanceId, String processKey, String businessTable,
                            long businessId, String businessKey, int round, String commandId,
                            long aggregateSequence, Instant occurredAt, String correlationId,
                            String causationId, WorkflowActorSnapshot actor, WorkflowPayload payload) {
    public WorkflowEvent {
        WorkflowEventValidation.uuid(eventId, "eventId");
        WorkflowEventValidation.uuid(commandId, "commandId");
        WorkflowEventValidation.uuid(correlationId, "correlationId");
        if (causationId != null) WorkflowEventValidation.uuid(causationId, "causationId");
        if (schemaVersion != 1 || type == null || actor == null || payload == null) {
            throw new IllegalArgumentException("事件版本或类型无效");
        }
        if (!"default".equals(tenantScope) || !tenantScope.equals(actor.tenantScope())) {
            throw new IllegalArgumentException("租户范围不匹配");
        }
        if (!"demo_leave_approval".equals(processKey) || !"demo_leave_request".equals(businessTable)
                || businessId <= 0 || round <= 0) {
            throw new IllegalArgumentException("业务关联无效");
        }
        WorkflowEventValidation.text(businessKey, "businessKey", 255);
        WorkflowEventValidation.instant(occurredAt, "occurredAt");
        if (processInstanceId != null) WorkflowEventValidation.text(processInstanceId, "processInstanceId", 64);

        switch (type) {
            case WORKFLOW_START_REQUESTED -> {
                if (!"upms".equals(sourceOwner) || !"workflow".equals(targetOwner)
                        || processInstanceId != null || aggregateSequence != 0
                        || !(payload instanceof WorkflowStartRequested)) {
                    throw new IllegalArgumentException("启动请求关联无效");
                }
            }
            case WORKFLOW_STARTED -> {
                if (!workflowToUpms(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence != 1 || !(payload instanceof WorkflowStarted)) {
                    throw new IllegalArgumentException("启动成功关联无效");
                }
            }
            case WORKFLOW_START_REJECTED -> {
                if (!workflowToUpms(sourceOwner, targetOwner) || processInstanceId != null
                        || aggregateSequence != 1 || !(payload instanceof WorkflowStartRejected)) {
                    throw new IllegalArgumentException("启动拒绝关联无效");
                }
            }
            case WORKFLOW_COMPLETED -> {
                if (!workflowToUpms(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence <= 1 || !(payload instanceof WorkflowCompleted)) {
                    throw new IllegalArgumentException("流程完成关联无效");
                }
            }
            case WORKFLOW_BUSINESS_TASK_REQUESTED -> {
                if (!workflowToUpms(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence <= 1 || !(payload instanceof WorkflowBusinessTaskRequested)) {
                    throw new IllegalArgumentException("自动任务请求关联无效");
                }
            }
            case WORKFLOW_BUSINESS_TASK_RESULT -> {
                if (!upmsToWorkflow(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence <= 1 || !(payload instanceof WorkflowBusinessTaskResult)) {
                    throw new IllegalArgumentException("自动任务结果关联无效");
                }
            }
            case WORKFLOW_COMPENSATION_REQUESTED -> {
                if (!workflowToUpms(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence <= 1 || !(payload instanceof WorkflowCompensationRequested)) {
                    throw new IllegalArgumentException("补偿请求关联无效");
                }
            }
            case WORKFLOW_COMPENSATION_RESULT -> {
                if (!upmsToWorkflow(sourceOwner, targetOwner) || processInstanceId == null
                        || aggregateSequence <= 1 || !(payload instanceof WorkflowCompensationResult)) {
                    throw new IllegalArgumentException("补偿结果关联无效");
                }
            }
        }
    }

    private static boolean workflowToUpms(String source, String target) {
        return "workflow".equals(source) && "upms".equals(target);
    }

    private static boolean upmsToWorkflow(String source, String target) {
        return "upms".equals(source) && "workflow".equals(target);
    }
}
