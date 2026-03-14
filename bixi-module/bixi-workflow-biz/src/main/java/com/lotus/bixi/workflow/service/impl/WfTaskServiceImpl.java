package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.TaskCommentDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.api.vo.TaskVO;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.flowable.engine.task.Comment;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class WfTaskServiceImpl implements WfTaskService {

    private final org.flowable.engine.TaskService taskService;

    private final RuntimeService runtimeService;

    private final HistoryService historyService;

    private final ApprovalRecordService approvalRecordService;

    private final FormDataService formDataService;

    @Override
    public IPage<TaskVO> todoPage(Page page, Long userId) {
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
    public IPage<TaskVO> donePage(Page page, Long userId) {
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
    @Transactional(rollbackFor = Exception.class)
    public void complete(TaskCompleteDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        Task task = taskService.createTaskQuery()
                .taskId(dto.getTaskId())
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在");
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
        record.setApprovalType(StrUtil.isNotBlank(dto.getApprovalType()) ? dto.getApprovalType() : WorkflowConstants.APPROVAL_TYPE_APPROVE);
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
    @Transactional(rollbackFor = Exception.class)
    public void reject(TaskRejectDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        Task task = taskService.createTaskQuery()
                .taskId(dto.getTaskId())
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        if (StrUtil.isNotBlank(dto.getTargetActivityId())) {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), dto.getTargetActivityId())
                    .changeState();
        } else {
            runtimeService.deleteProcessInstance(task.getProcessInstanceId(), dto.getRejectReason());
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(TaskTransferDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        Task task = taskService.createTaskQuery()
                .taskId(dto.getTaskId())
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        taskService.setAssignee(dto.getTaskId(), String.valueOf(dto.getTransferUserId()));

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(dto.getTaskId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_TRANSFER);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getTransferReason());
        record.setDelegateUserId(dto.getTransferUserId());
        record.setDelegateUserName(dto.getTransferUserName());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegate(TaskTransferDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        Task task = taskService.createTaskQuery()
                .taskId(dto.getTaskId())
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        taskService.delegateTask(dto.getTaskId(), String.valueOf(dto.getTransferUserId()));

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(dto.getTaskId());
        record.setTaskName(task.getName());
        record.setTaskKey(task.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_DELEGATE);
        record.setApprovalUserId(user.getId());
        record.setApprovalUserName(user.getUsername());
        record.setApprovalComment(dto.getTransferReason());
        record.setDelegateUserId(dto.getTransferUserId());
        record.setDelegateUserName(dto.getTransferUserName());
        record.setApprovalTime(LocalDateTime.now());
        approvalRecordService.saveRecord(record);
    }

    @Override
    public TaskVO getById(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId)
                    .singleResult();
            if (historicTask != null) {
                return convertHistoricToVO(historicTask);
            }
            return null;
        }

        return convertToVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(String taskId, Long userId) {
        taskService.claim(taskId, String.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unclaim(String taskId) {
        taskService.unclaim(taskId);
    }

    @Override
    public Object getComments(String taskId) {
        List<Comment> comments = taskService.getTaskComments(taskId);
        return comments.stream().map(Comment::getFullMessage).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(TaskCommentDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        Task task = taskService.createTaskQuery()
                .taskId(dto.getTaskId())
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        taskService.addComment(task.getProcessInstanceId(), task.getId(), dto.getMessage());
        log.info("Added comment to task {} by user {}", dto.getTaskId(), user.getUsername());
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
