package com.lotus.bixi.workflow.api.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Immutable workflow-to-UPMS contract for one assigned or candidate user-task notification. */
public record WorkflowTaskNotification(
        String eventId,
        int schemaVersion,
        String sourceOwner,
        String targetOwner,
        String tenantScope,
        String processDefinitionId,
        String processInstanceId,
        String processKey,
        String taskId,
        String taskName,
        List<Long> recipientUserIds,
        List<Long> recipientRoleIds,
        String businessKey,
        String commandId,
        String operationId,
        Instant occurredAt) {

    public static final String TYPE = "WORKFLOW_TASK_NOTIFICATION_REQUESTED";
    public static final int MAX_RECIPIENT_IDENTITIES = 128;

    public WorkflowTaskNotification {
        uuid(eventId, "eventId");
        uuid(commandId, "commandId");
        uuid(operationId, "operationId");
        if ((schemaVersion != 1 && schemaVersion != 2)
                || !"workflow".equals(sourceOwner) || !"upms".equals(targetOwner)) {
            throw new IllegalArgumentException("任务通知版本或路由无效");
        }
        if (tenantScope == null || !tenantScope.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("任务通知租户范围无效");
        }
        try {
            if (Long.parseLong(tenantScope) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("任务通知租户范围无效");
        }
        text(processDefinitionId, "processDefinitionId", 255);
        text(processInstanceId, "processInstanceId", 128);
        if (!isValidProcessKey(processKey)) {
            throw new IllegalArgumentException("任务通知流程标识无效");
        }
        text(taskId, "taskId", 128);
        text(taskName, "taskName", 160);
        recipientUserIds = identities(recipientUserIds, "用户");
        recipientRoleIds = identities(recipientRoleIds, "角色");
        if (recipientUserIds.size() + recipientRoleIds.size() > MAX_RECIPIENT_IDENTITIES
                || recipientUserIds.isEmpty() && recipientRoleIds.isEmpty()
                || schemaVersion == 1 && (recipientUserIds.size() != 1 || !recipientRoleIds.isEmpty())) {
            throw new IllegalArgumentException("任务通知收件人无效");
        }
        if (businessKey != null) text(businessKey, "businessKey", 255);
        if (occurredAt == null) throw new IllegalArgumentException("任务通知发生时间无效");
    }

    private static List<Long> identities(List<Long> values, String kind) {
        if (values == null) {
            throw new IllegalArgumentException("任务通知" + kind + "收件人无效");
        }
        TreeSet<Long> ordered = new TreeSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("任务通知" + kind + "收件人无效");
            }
            ordered.add(value);
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    public static boolean isValidProcessKey(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    }

    private static void uuid(String value, String field) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException(field + "无效");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private static void text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "无效");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(field + "无效");
            }
        }
    }
}
