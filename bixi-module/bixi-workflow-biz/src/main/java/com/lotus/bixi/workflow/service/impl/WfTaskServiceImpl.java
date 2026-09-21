package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.fasterxml.jackson.databind.JsonNode;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.TaskCommentDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.dto.TaskResolveDTO;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import com.lotus.bixi.workflow.command.WorkflowCommandExecutor;
import com.lotus.bixi.workflow.command.WorkflowRequestHasher;
import com.lotus.bixi.workflow.service.ApprovalRecordService;
import com.lotus.bixi.workflow.service.FormDataService;
import com.lotus.bixi.workflow.service.WfTaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.flowable.task.api.DelegationState;
import org.flowable.common.engine.impl.identity.Authentication;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.flowable.engine.task.Comment;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class WfTaskServiceImpl implements WfTaskService {

    private final org.flowable.engine.TaskService taskService;

    private final RuntimeService runtimeService;

    private final HistoryService historyService;

    private final ApprovalRecordService approvalRecordService;

    private final FormDataService formDataService;

    private final WorkflowAccessService access;

    private final WfProcessInstanceMapper instances;
    private final WorkflowResultNotifier results;
    private final WorkflowCommandExecutor commands;
    private final WorkflowRequestHasher hasher;

    private static final Set<String> IDENTITY_VARIABLES = Set.of("applicant", "initiator", "startUserId",
            "approverId", "tenantId", "businessKey", "businessId", "businessTable", "round", "businessRound", "requestId");

    @Override
    @HasPermission("workflow_task_view")
    public IPage<TaskVO> todoPage(Page page, Long userId) {
        access.requireCurrentUser(userId);
        access.validatePage(page);
        long total = taskService.createTaskQuery()
                .taskCandidateOrAssigned(String.valueOf(userId))
                .active()
                .count();

        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateOrAssigned(String.valueOf(userId))
                .active()
                .orderByTaskCreateTime()
                .desc()
                .listPage((int) ((page.getCurrent() - 1) * page.getSize()), (int) page.getSize());

        List<TaskVO> records = new ArrayList<>();
        for (Task task : tasks) {
            TaskVO vo = convertToVO(task);
            records.add(vo);
        }

        IPage<TaskVO> resultPage = new Page<>(page.getCurrent(), page.getSize(), total);
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @HasPermission("workflow_task_view")
    public IPage<TaskVO> donePage(Page page, Long userId) {
        access.requireCurrentUser(userId);
        access.validatePage(page);
        long total = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .count();

        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage((int) ((page.getCurrent() - 1) * page.getSize()), (int) page.getSize());

        List<TaskVO> records = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            TaskVO vo = convertHistoricToVO(task);
            records.add(vo);
        }

        IPage<TaskVO> resultPage = new Page<>(page.getCurrent(), page.getSize(), total);
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void complete(TaskCompleteDTO dto) {
        BixiUser user = access.currentUser();
        TaskCompleteDTO normalized = normalizeComplete(dto);
        executeTaskCommand("COMPLETE", normalized.getTaskId(), user, normalized.getRequestId(),
                taskPayload(normalized), normalized.getTaskId(), task -> completeOnce(normalized, user, task));
    }

    private void completeOnce(TaskCompleteDTO dto, BixiUser user, Task task) {

        if (StrUtil.isNotBlank(dto.getApprovalType())
                && !WorkflowConstants.APPROVAL_TYPE_APPROVE.equals(dto.getApprovalType())) {
            throw new IllegalArgumentException("完成任务仅支持审批通过，请使用对应操作接口");
        }
        if (task.getDelegationState() == DelegationState.PENDING) {
            throw new IllegalArgumentException("委派任务须先解决委派");
        }
        if (dto.getVariables() != null && dto.getVariables().keySet().stream().anyMatch(IDENTITY_VARIABLES::contains)) {
            throw new IllegalArgumentException("审批不能改写流程身份或业务关联变量");
        }
        if (dto.getVariables() != null) {
            taskService.complete(dto.getTaskId(), dto.getVariables());
        } else {
            taskService.complete(dto.getTaskId());
        }

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(dto.getTaskId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_APPROVE);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getApprovalComment());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);

        if (dto.getFormId() != null && StrUtil.isNotBlank(dto.getFormDataJson())) {
            FormDataDTO formDataDTO = new FormDataDTO();
            formDataDTO.setFormId(dto.getFormId());
            formDataDTO.setProcessInstanceId(task.getProcessInstanceId());
            formDataDTO.setTaskId(dto.getTaskId());
            formDataDTO.setDataJson(dto.getFormDataJson());
            formDataDTO.setSubmitUserId(user.getId());
            formDataDTO.setSubmitUserName(user.getUsername());
            formDataDTO.setSubmitTime(LocalDateTime.now());
            formDataService.saveFormData(formDataDTO);
        }
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void reject(TaskRejectDTO dto) {
        BixiUser user = access.currentUser();
        TaskRejectDTO normalized = normalizeReject(dto);
        executeTaskCommand("REJECT", normalized.getTaskId(), user, normalized.getRequestId(),
                taskPayload(normalized), normalized.getTaskId(), task -> rejectOnce(normalized, user, task));
    }

    private void rejectOnce(TaskRejectDTO dto, BixiUser user, Task task) {

        if (StrUtil.isNotBlank(dto.getTargetActivityId())) {
            throw new IllegalArgumentException("拒绝表示结束流程，不支持指定跳转节点");
        }
        if (task.getDelegationState() == DelegationState.PENDING) {
            throw new IllegalArgumentException("委派任务须先解决委派");
        }
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), dto.getRejectReason());
        int updated = instances.update(null, Wrappers.<WfProcessInstance>lambdaUpdate()
                .eq(WfProcessInstance::getProcessInstanceId, task.getProcessInstanceId())
                .eq(WfProcessInstance::getStatus, WorkflowConstants.STATUS_RUNNING)
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_REJECTED)
                .set(WfProcessInstance::getEndTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new IllegalStateException("流程状态已变更");
        }

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(dto.getTaskId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_REJECT);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getRejectReason());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);
        results.afterCommit(task.getProcessInstanceId());
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void transfer(TaskTransferDTO dto) {
        BixiUser user = access.currentUser();
        executeTaskCommand("TRANSFER", dto.getTaskId(), user, dto.getRequestId(),
                taskPayload(dto), null, task -> transferOnce(dto, user, task, false));
    }

    private void transferOnce(TaskTransferDTO dto, BixiUser user, Task task, boolean delegation) {

        if (dto.getTransferUserId() == null || dto.getTransferUserId() <= 0
                || user.getId().equals(dto.getTransferUserId())) {
            throw new IllegalArgumentException("请选择其他有效办理人");
        }
        if (task.getDelegationState() == DelegationState.PENDING) {
            throw new IllegalArgumentException("委派任务须先解决委派");
        }
        if (delegation) {
            // Each new delegation belongs to its current assignee, including tasks reclaimed
            // after an earlier delegation was resolved. Nested pending delegations are rejected.
            taskService.setOwner(dto.getTaskId(), user.getId().toString());
            taskService.delegateTask(dto.getTaskId(), String.valueOf(dto.getTransferUserId()));
        } else {
            taskService.setAssignee(dto.getTaskId(), String.valueOf(dto.getTransferUserId()));
        }
        if (!delegation) {
            // A permanent transfer ends the previous ownership, including resolved delegation.
            // Flowable will capture the new assignee as owner if the task is delegated again.
            taskService.setOwner(dto.getTaskId(), null);
        }

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(dto.getTaskId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(delegation ? WorkflowConstants.APPROVAL_TYPE_DELEGATE : WorkflowConstants.APPROVAL_TYPE_TRANSFER);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getTransferReason());
        record.setDelegateUserId(dto.getTransferUserId());
        record.setDelegateUserName(dto.getTransferUserName());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);
    }
    
    @Override
    @HasPermission("workflow_task_edit")
    public void delegate(TaskTransferDTO dto) {
        BixiUser user = access.currentUser();
        executeTaskCommand("DELEGATE", dto.getTaskId(), user, dto.getRequestId(),
                taskPayload(dto), null, task -> transferOnce(dto, user, task, true));
    }

    @Override
    @HasPermission("workflow_task_view")
    public TaskVO getById(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId)
                    .singleResult();
            if (historicTask != null) {
                access.requireProcessView(historicTask.getProcessInstanceId());
                return convertHistoricToVO(historicTask);
            }
            throw new IllegalArgumentException("任务不存在");
        }

        access.requireProcessView(task.getProcessInstanceId());
        return convertToVO(task);
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void resolve(TaskResolveDTO dto) {
        BixiUser user = access.currentUser();
        executeTaskCommand("RESOLVE", dto.getTaskId(), user, dto.getRequestId(),
                taskPayload(dto), null, task -> resolveOnce(dto, user, task));
    }

    private void resolveOnce(TaskResolveDTO dto, BixiUser user, Task task) {
        if (task.getDelegationState() != DelegationState.PENDING || StrUtil.isBlank(task.getOwner())) {
            throw new IllegalArgumentException("任务不在待解决的委派状态");
        }
        taskService.resolveTask(task.getId());
        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(task.getId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_RESOLVE);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getComment());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void claim(String taskId, Long userId) {
        throw new IllegalArgumentException("requestId必须是标准小写UUID");
    }

    @HasPermission("workflow_task_edit")
    public void claim(String taskId, Long userId, String requestId) {
        BixiUser user = access.currentUser();
        access.requireCurrentUser(userId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        executeTaskCommand("CLAIM", taskId, user, requestId, hasher.normalize(payload), null, false,
                ignored -> claimOnce(taskId, userId));
    }

    private void claimOnce(String taskId, Long userId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已挂起");
        }
        if (task.getAssignee() != null || taskService.getIdentityLinksForTask(taskId).stream()
                .noneMatch(link -> "candidate".equals(link.getType()) && userId.toString().equals(link.getUserId()))) {
            throw new AccessDeniedException("只有任务候选人可认领未分配的任务");
        }
        taskService.claim(taskId, userId.toString());
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void unclaim(String taskId) {
        throw new IllegalArgumentException("requestId必须是标准小写UUID");
    }

    @HasPermission("workflow_task_edit")
    public void unclaim(String taskId, String requestId) {
        BixiUser user = access.currentUser();
        executeTaskCommand("UNCLAIM", taskId, user, requestId, hasher.normalize(Map.of()), null,
                ignored -> unclaimOnce(taskId));
    }

    private void unclaimOnce(String taskId) {
        Task task = access.requireAssignee(taskId);
        if (task.getDelegationState() == DelegationState.PENDING || taskService.getIdentityLinksForTask(taskId).stream()
                .noneMatch(link -> "candidate".equals(link.getType()) && link.getUserId() != null)) {
            throw new IllegalArgumentException("只有候选人认领的任务可取消认领");
        }
        taskService.unclaim(taskId);
    }

    @Override
    @HasPermission("workflow_task_view")
    public Object getComments(String taskId) {
        getById(taskId);
        List<Comment> comments = taskService.getTaskComments(taskId);
        return comments.stream().map(Comment::getFullMessage).collect(Collectors.toList());
    }

    @Override
    @HasPermission("workflow_task_edit")
    public void addComment(TaskCommentDTO dto) {
        BixiUser user = access.currentUser();
        executeTaskCommand("COMMENT", dto.getTaskId(), user, dto.getRequestId(),
                taskPayload(dto), null, task -> addCommentOnce(dto, user, task));
    }

    private void addCommentOnce(TaskCommentDTO dto, BixiUser user, Task task) {

        String previousUser = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId(user.getId().toString());
            taskService.addComment(task.getId(), task.getProcessInstanceId(), dto.getMessage());
        } finally {
            Authentication.setAuthenticatedUserId(previousUser);
        }
        log.info("Added comment to task {} by user {}", dto.getTaskId(), user.getUsername());
    }

    private void executeTaskCommand(String operation, String taskId, BixiUser user, String requestId,
            JsonNode payload, String terminalTaskId, java.util.function.Consumer<Task> work) {
        executeTaskCommand(operation, taskId, user, requestId, payload, terminalTaskId, true, work);
    }

    private void executeTaskCommand(String operation, String taskId, BixiUser user, String requestId,
            JsonNode payload, String terminalTaskId, boolean requireAssignee, java.util.function.Consumer<Task> work) {
        WorkflowRequestDTO.requireRequestId(requestId);
        WorkflowRequestHasher.Actor actor = new WorkflowRequestHasher.Actor(
                WorkflowCommandExecutor.TENANT_SCOPE, user.getId());
        commands.executeWithProcessLookup(new WorkflowCommandExecutor.CommandInput(requestId, operation, taskId, "workflow",
                        user.getId(), user.getUsername(), actor.tenantScope(), payload,
                        hasher.hash(operation, taskId, "workflow", actor, payload), terminalTaskId, null),
                WorkflowCommandExecutor.CommandResult.class, () -> {
                    String processId = locateProcessId(taskId, user, requireAssignee);
                    if (processId == null) throw new IllegalArgumentException("任务不存在或已结束");
                    return processId;
                }, processId -> {
                    if (instances.selectForUpdate(processId) == null) throw new IllegalArgumentException("流程实例不存在");
                    Task current = requireAssignee ? access.requireAssignee(taskId)
                            : taskService.createTaskQuery().taskId(taskId).active().singleResult();
                    if (current == null) throw new IllegalArgumentException("任务不存在或已挂起");
                    work.accept(current);
                    return new WorkflowCommandExecutor.CommandResult(processId);
                });
    }

    private String locateProcessId(String taskId, BixiUser user, boolean requireAssignee) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) return task.getProcessInstanceId();
        HistoricTaskInstance historic = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (historic == null) return null;
        if (requireAssignee && !user.getId().toString().equals(historic.getAssignee())) {
            throw new AccessDeniedException("只有原办理人可重试已结束任务");
        }
        return historic.getProcessInstanceId();
    }

    private JsonNode taskPayload(Object dto) {
        Map<String, Object> payload = new HashMap<>();
        if (dto instanceof TaskCompleteDTO value) {
            payload.put("approvalComment", value.getApprovalComment());
            payload.put("approvalType", value.getApprovalType());
            payload.put("variables", value.getVariables());
            payload.put("formDataJson", value.getFormDataJson() == null ? null : hasher.parse(value.getFormDataJson()));
            payload.put("formId", value.getFormId());
        } else if (dto instanceof TaskRejectDTO value) {
            payload.put("rejectReason", value.getRejectReason());
            payload.put("targetActivityId", value.getTargetActivityId());
        } else if (dto instanceof TaskTransferDTO value) {
            payload.put("transferUserId", value.getTransferUserId());
            payload.put("transferUserName", value.getTransferUserName());
            payload.put("transferReason", value.getTransferReason());
        } else if (dto instanceof TaskResolveDTO value) {
            payload.put("comment", value.getComment());
        } else if (dto instanceof TaskCommentDTO value) {
            payload.put("message", value.getMessage());
        }
        return hasher.normalize(payload);
    }

    private TaskCompleteDTO normalizeComplete(TaskCompleteDTO dto) {
        WorkflowRequestDTO.requireRequestId(dto.getRequestId());
        TaskCompleteDTO normalized = new TaskCompleteDTO();
        normalized.setRequestId(dto.getRequestId());
        normalized.setTaskId(dto.getTaskId());
        normalized.setApprovalComment(dto.getApprovalComment());
        normalized.setApprovalType(StrUtil.isBlank(dto.getApprovalType())
                ? WorkflowConstants.APPROVAL_TYPE_APPROVE : dto.getApprovalType());
        if (dto.getVariables() != null && !dto.getVariables().isEmpty()) {
            normalized.setVariables(hasher.variables(hasher.normalize(dto.getVariables())));
        }
        if (dto.getFormId() != null && StrUtil.isNotBlank(dto.getFormDataJson())) {
            normalized.setFormId(dto.getFormId());
            normalized.setFormDataJson(hasher.canonical(hasher.parse(dto.getFormDataJson())));
        }
        return normalized;
    }

    private TaskRejectDTO normalizeReject(TaskRejectDTO dto) {
        WorkflowRequestDTO.requireRequestId(dto.getRequestId());
        TaskRejectDTO normalized = new TaskRejectDTO();
        normalized.setRequestId(dto.getRequestId());
        normalized.setTaskId(dto.getTaskId());
        normalized.setRejectReason(dto.getRejectReason());
        normalized.setTargetActivityId(StrUtil.isBlank(dto.getTargetActivityId()) ? null : dto.getTargetActivityId());
        return normalized;
    }

    private TaskVO convertToVO(Task task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setTaskKey(task.getTaskDefinitionKey());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setAssignee(task.getAssignee());
        vo.setOwner(task.getOwner());
        vo.setDelegationState(task.getDelegationState() == null ? null : task.getDelegationState().name());
        vo.setCreateTime(convertToLocalDateTime(task.getCreateTime()));
        vo.setDueDate(convertToLocalDateTime(task.getDueDate()));
        vo.setPriority(task.getPriority());
        vo.setFormKey(task.getFormKey());

        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (processInstance != null) {
            vo.setProcessKey(processInstance.getProcessDefinitionKey());
            vo.setProcessName(processInstance.getProcessDefinitionName());
            vo.setBusinessKey(processInstance.getBusinessKey());
        }

        return vo;
    }

    private TaskVO convertHistoricToVO(HistoricTaskInstance task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setTaskKey(task.getTaskDefinitionKey());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setAssignee(task.getAssignee());
        vo.setOwner(task.getOwner());
        vo.setEndTime(convertToLocalDateTime(task.getEndTime()));
        vo.setCreateTime(convertToLocalDateTime(task.getCreateTime()));
        vo.setDueDate(convertToLocalDateTime(task.getDueDate()));
        vo.setPriority(task.getPriority());
        vo.setFormKey(task.getFormKey());

        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (processInstance != null) {
            vo.setProcessKey(processInstance.getProcessDefinitionKey());
            vo.setProcessName(processInstance.getProcessDefinitionName());
            vo.setBusinessKey(processInstance.getBusinessKey());
        }

        return vo;
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

}
