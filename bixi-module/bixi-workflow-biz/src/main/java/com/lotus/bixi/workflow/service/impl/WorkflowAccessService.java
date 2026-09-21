package com.lotus.bixi.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.mapper.WfApprovalRecordMapper;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Data access rules shared by HTTP and local workflow operations. */
@Service
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
public class WorkflowAccessService {
    private final WfProcessInstanceMapper instances;
    private final WfApprovalRecordMapper approvals;
    private final TaskService tasks;
    private final HistoryService history;

    public BixiUser currentUser() {
        BixiUser user = SecurityUtils.getUser();
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("请先登录");
        }
        return user;
    }

    public void requireCurrentUser(Long userId) {
        if (!currentUser().getId().equals(userId)) {
            throw new AccessDeniedException("只能查询或认领本人的任务");
        }
    }

    public WfProcessInstance requireProcess(String processInstanceId) {
        WfProcessInstance instance = instances.selectOne(Wrappers.<WfProcessInstance>lambdaQuery()
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return instance;
    }

    public WfProcessInstance requireProcessView(String processInstanceId) {
        Long userId = currentUser().getId();
        WfProcessInstance instance = requireProcess(processInstanceId);
        String identity = userId.toString();
        if (userId.equals(instance.getStartUserId())
                || tasks.createTaskQuery().processInstanceId(processInstanceId).taskCandidateOrAssigned(identity).count() > 0
                || history.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId).taskAssignee(identity).count() > 0
                || approvals.selectCount(Wrappers.<WfApprovalRecord>lambdaQuery()
                    .eq(WfApprovalRecord::getProcessInstanceId, processInstanceId)
                    .eq(WfApprovalRecord::getApprovalUserId, userId)) > 0) {
            return instance;
        }
        throw new AccessDeniedException("无权访问此流程");
    }

    public WfProcessInstance requireStarter(String processInstanceId) {
        WfProcessInstance instance = requireProcess(processInstanceId);
        if (!currentUser().getId().equals(instance.getStartUserId())) {
            throw new AccessDeniedException("仅发起人可操作此流程");
        }
        return instance;
    }

    public Task requireAssignee(String taskId) {
        String userId = currentUser().getId().toString();
        Task task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已结束");
        }
        if (!userId.equals(task.getAssignee())) {
            throw new AccessDeniedException("只有当前办理人可操作任务");
        }
        if (task.isSuspended()) {
            throw new IllegalArgumentException("任务已挂起");
        }
        return task;
    }

    public void validatePage(Page<?> page) {
        if (page.getCurrent() < 1 || page.getSize() < 1 || page.getSize() > 200
                || page.getCurrent() > Integer.MAX_VALUE / page.getSize()) {
            throw new IllegalArgumentException("分页参数超出范围");
        }
    }
}
