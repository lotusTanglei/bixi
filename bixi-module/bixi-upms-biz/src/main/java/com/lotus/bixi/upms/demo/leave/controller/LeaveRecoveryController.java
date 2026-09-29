package com.lotus.bixi.upms.demo.leave.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.service.UpmsRecoveryService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryBusinessTaskVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryCommandVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryEventVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryPageVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Applicant-scoped recovery views for the demo leave owner. */
@RestController
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
@RequestMapping("/demo/leave/recovery")
@Tag(name = "demo-leave-recovery", description = "请假可靠投递恢复管理")
public class LeaveRecoveryController {
    private final UpmsRecoveryService recovery;

    @GetMapping("/events/page")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "分页查询请假事件")
    public R<WorkflowRecoveryPageVO<WorkflowRecoveryEventVO>> events(
            @Valid WorkflowRecoveryQueryDTO query) {
        return R.ok(recovery.pageLeaveEvents(query));
    }

    @GetMapping("/events/{eventId}")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "查询请假事件详情")
    public R<WorkflowRecoveryEventVO> event(@PathVariable String eventId) {
        return R.ok(recovery.leaveEvent(eventId));
    }

    @GetMapping("/commands/page")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "分页查询请假命令")
    public R<WorkflowRecoveryPageVO<WorkflowRecoveryCommandVO>> commands(
            @Valid WorkflowRecoveryQueryDTO query) {
        return R.ok(recovery.pageLeaveCommands(query));
    }

    @GetMapping("/commands/{commandId}")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "查询请假命令详情")
    public R<WorkflowRecoveryCommandVO> command(@PathVariable String commandId) {
        return R.ok(recovery.leaveCommand(commandId));
    }

    @GetMapping("/business-tasks/page")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "分页查询请假登记任务")
    public R<WorkflowRecoveryPageVO<WorkflowRecoveryBusinessTaskVO>> businessTasks(
            @Valid WorkflowRecoveryQueryDTO query) {
        return R.ok(recovery.pageLeaveBusinessTasks(query));
    }

    @GetMapping("/business-tasks/{operationId}")
    @HasPermission("demo_leave_recovery_view")
    @Operation(summary = "查询请假登记任务详情")
    public R<WorkflowRecoveryBusinessTaskVO> businessTask(@PathVariable String operationId) {
        return R.ok(recovery.leaveBusinessTask(operationId));
    }

    @PostMapping("/events/{eventId}/retry")
    @SysLog("重试请假事件")
    @HasPermission("demo_leave_recovery_edit")
    @Operation(summary = "重试请假事件")
    public R<WorkflowRecoveryResultVO> retryEvent(@PathVariable String eventId,
            @Valid @RequestBody WorkflowRecoveryRequestDTO request) {
        return R.ok(recovery.retryLeaveEvent(eventId, request));
    }

    @PostMapping("/commands/{commandId}/retry")
    @SysLog("重试请假命令")
    @HasPermission("demo_leave_recovery_edit")
    @Operation(summary = "重试请假命令")
    public R<WorkflowRecoveryResultVO> retryCommand(@PathVariable String commandId,
            @Valid @RequestBody WorkflowRecoveryRequestDTO request) {
        return R.ok(recovery.retryLeaveCommand(commandId, request));
    }

    @PostMapping("/business-tasks/{operationId}/reconcile")
    @SysLog("对账请假登记")
    @HasPermission("demo_leave_recovery_edit")
    @Operation(summary = "对账请假登记任务")
    public R<WorkflowRecoveryResultVO> reconcileBooking(@PathVariable String operationId,
            @Valid @RequestBody WorkflowRecoveryRequestDTO request) {
        return R.ok(recovery.reconcileLeaveBooking(operationId, request));
    }
}
