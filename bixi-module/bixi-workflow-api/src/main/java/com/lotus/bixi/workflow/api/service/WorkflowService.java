package com.lotus.bixi.workflow.api.service;

import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Workflow operations available to the authenticated caller in either deployment mode.
 * Adapters inherit these parameter constraints and must not redefine them on overrides.
 */
public interface WorkflowService {

    R<ProcessInstanceVO> startProcess(@NotNull @Valid ProcessStartDTO dto);

    R<WorkflowCommandVO> getCommand(String requestId);

    R<ProcessInstanceVO> getProcessInstance(String processInstanceId);

    R<List<ApprovalRecordVO>> getApprovalHistory(String processInstanceId);

    R<Void> completeTask(@NotNull @Valid TaskCompleteDTO dto);

    R<Void> rejectTask(@NotNull @Valid TaskRejectDTO dto);

    R<Void> transferTask(@NotNull @Valid TaskTransferDTO dto);

    R<Page<TaskVO>> getTodoTasks(@Positive long current, @Positive long size);

    R<Page<TaskVO>> getDoneTasks(@Positive long current, @Positive long size);
}
