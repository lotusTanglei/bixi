package com.lotus.bixi.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.dto.TaskCommentDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import com.lotus.bixi.workflow.service.WfTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/task")
@Tag(description = "task", name = "任务管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class TaskController {

    private final WfTaskService wfTaskService;

    @GetMapping("/todo/page")
    @HasPermission("wf_task_complete")
    @Operation(summary = "查询待办任务")
    public R<IPage<TaskVO>> todo(Page<TaskVO> page) {
        Long userId = SecurityUtils.getUser().getId();
        return R.ok(wfTaskService.todoPage(page, userId));
    }

    @GetMapping("/done/page")
    @HasPermission("wf_task_complete")
    @Operation(summary = "查询已办任务")
    public R<IPage<TaskVO>> done(Page<TaskVO> page) {
        Long userId = SecurityUtils.getUser().getId();
        return R.ok(wfTaskService.donePage(page, userId));
    }

    @GetMapping("/details/{taskId}")
    @HasPermission("wf_task_complete")
    @Operation(summary = "查询任务详情")
    public R<TaskVO> getByTaskId(@PathVariable String taskId) {
        return R.ok(wfTaskService.getById(taskId));
    }

    @PostMapping("/complete")
    @SysLog("完成任务")
    @HasPermission("wf_task_complete")
    @Operation(summary = "完成任务")
    public R<Void> complete(@Valid @RequestBody TaskCompleteDTO completeDTO) {
        wfTaskService.complete(completeDTO);
        return R.ok();
    }

    @PostMapping("/reject")
    @SysLog("驳回任务")
    @HasPermission("wf_task_complete")
    @Operation(summary = "驳回任务")
    public R<Void> reject(@Valid @RequestBody TaskRejectDTO rejectDTO) {
        wfTaskService.reject(rejectDTO);
        return R.ok();
    }

    @PostMapping("/transfer")
    @SysLog("转办任务")
    @HasPermission("wf_task_complete")
    @Operation(summary = "转办任务")
    public R<Void> transfer(@Valid @RequestBody TaskTransferDTO transferDTO) {
        wfTaskService.transfer(transferDTO);
        return R.ok();
    }
    
    @PostMapping("/delegate")
    @SysLog("委派任务")
    @HasPermission("wf_task_complete")
    @Operation(summary = "委派任务")
    public R<Void> delegate(@Valid @RequestBody TaskTransferDTO delegateDTO) {
        wfTaskService.delegate(delegateDTO);
        return R.ok();
    }

    @PostMapping("/claim")
    @SysLog("认领任务")
    @HasPermission("wf_task_complete")
    @Operation(summary = "认领任务")
    public R<Void> claim(@RequestParam String taskId, @RequestParam Long userId) {
        wfTaskService.claim(taskId, userId);
        return R.ok();
    }
    
    @PostMapping("/unclaim/{taskId}")
    @SysLog("取消认领")
    @HasPermission("wf_task_complete")
    @Operation(summary = "取消认领")
    public R<Void> unclaim(@PathVariable String taskId) {
        wfTaskService.unclaim(taskId);
        return R.ok();
    }
    
    @GetMapping("/comment/{taskId}")
    @HasPermission("wf_task_complete")
    @Operation(summary = "获取任务评论")
    public R<Object> getComments(@PathVariable String taskId) {
        return R.ok(wfTaskService.getComments(taskId));
    }
    
    @PostMapping("/comment")
    @SysLog("添加评论")
    @HasPermission("wf_task_complete")
    @Operation(summary = "添加评论")
    public R<Void> addComment(@Valid @RequestBody TaskCommentDTO commentDTO) {
        wfTaskService.addComment(commentDTO);
        return R.ok();
    }
}
