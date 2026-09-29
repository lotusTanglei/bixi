package com.lotus.bixi.workflow.listener;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.lotus.bixi.common.workflow.listener.BaseTaskListener;
import com.lotus.bixi.workflow.event.WorkflowTaskNotificationSink;
import com.lotus.bixi.workflow.api.identity.WorkflowCandidateGroup;
import lombok.extern.slf4j.Slf4j;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@ConditionalOnWorkflowEnabled
@Component
public class TaskCreateListener extends BaseTaskListener {

    static final String RECORDED_MARKER = "bixiTaskNotificationRecorded";

    private final ObjectProvider<WorkflowTaskNotificationSink> notifications;

    public TaskCreateListener(ObjectProvider<WorkflowTaskNotificationSink> notifications) {
        this.notifications = notifications;
    }

    @Override
    protected void doNotify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String assignee = delegateTask.getAssignee();
        String processInstanceId = delegateTask.getProcessInstanceId();

        log.info("任务创建 - taskId: {}, taskName: {}, assignee: {}, processInstanceId: {}",
                taskId, taskName, assignee, processInstanceId);

        WorkflowTaskNotificationSink sink = notifications.getIfAvailable();
        Recipients recipients = recipients(delegateTask, assignee);
        if (!recipients.empty() && sink != null
                && !Boolean.TRUE.equals(delegateTask.getTransientVariableLocal(RECORDED_MARKER))) {
            sink.record(context(delegateTask, recipients));
            delegateTask.setTransientVariableLocal(RECORDED_MARKER, Boolean.TRUE);
        }
    }

    private static WorkflowTaskNotificationSink.Context context(DelegateTask task, Recipients recipients) {
        String definitionId = task.getProcessDefinitionId();
        int separator = definitionId == null ? -1 : definitionId.indexOf(':');
        String processKey = separator > 0 ? definitionId.substring(0, separator) : definitionId;
        String processInstanceId = task.getProcessInstanceId();
        String taskId = task.getId();
        String taskName = task.getName() == null || task.getName().isBlank()
                ? task.getTaskDefinitionKey() : task.getName();
        String tenantScope = variable(task, "tenantScope");
        String businessKey = nullableVariable(task, "businessKey");
        String commandId = canonicalUuid(nullableVariable(task, "startRequestId"))
                ? variable(task, "startRequestId") : namedId(processInstanceId + ":start-command");
        String operationId = namedId(taskId + ":notification");
        return new WorkflowTaskNotificationSink.Context(definitionId, processInstanceId, processKey,
                taskId, taskName, recipients.userIds(), recipients.roleIds(), tenantScope, businessKey,
                commandId, operationId, Instant.now());
    }

    private static Recipients recipients(DelegateTask task, String assignee) {
        if (assignee != null) {
            return new Recipients(List.of(positiveId(assignee, "审批人")), List.of());
        }
        TreeSet<Long> users = new TreeSet<>();
        TreeSet<Long> roles = new TreeSet<>();
        Set<IdentityLink> candidates = task.getCandidates();
        if (candidates != null) {
            for (IdentityLink candidate : candidates) {
                if (candidate == null) continue;
                if (candidate.getUserId() != null) {
                    users.add(positiveId(candidate.getUserId(), "候选用户"));
                }
                if (candidate.getGroupId() != null) {
                    try {
                        roles.add(WorkflowCandidateGroup.parse(candidate.getGroupId()).roleId());
                    } catch (IllegalArgumentException invalid) {
                        throw new IllegalArgumentException("工作流任务候选角色无效", invalid);
                    }
                }
            }
        }
        return new Recipients(new ArrayList<>(users), new ArrayList<>(roles));
    }

    private static long positiveId(String value, String kind) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("工作流任务" + kind + "必须是正十进制ID");
        }
    }

    private record Recipients(List<Long> userIds, List<Long> roleIds) {
        private boolean empty() {
            return userIds.isEmpty() && roleIds.isEmpty();
        }
    }

    private static String variable(DelegateTask task, String name) {
        String value = nullableVariable(task, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("工作流任务缺少" + name);
        return value;
    }

    private static String nullableVariable(DelegateTask task, String name) {
        Object value = task.getVariable(name);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean canonicalUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String namedId(String identity) {
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
