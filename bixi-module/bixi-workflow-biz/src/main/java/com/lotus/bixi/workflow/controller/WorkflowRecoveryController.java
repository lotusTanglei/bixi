package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.WorkflowInboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowOutboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowQuarantineSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReconciliationVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReplayVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRuntimeDiagnosticsVO;
import com.lotus.bixi.workflow.service.impl.WorkflowRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Explicit operator entry point; it is absent when reliable delivery is disabled. */
@RestController
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
@RequestMapping("/workflow/recovery")
@Tag(name = "workflow-recovery", description = "可靠投递恢复管理")
public class WorkflowRecoveryController {
    private final WorkflowRecoveryService recovery;

    @GetMapping("/outbox")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询工作流 Outbox")
    public R<List<WorkflowOutboxSnapshotVO>> outbox(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listOutbox(status, limit));
    }

    @GetMapping("/inbox")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询工作流 Inbox")
    public R<List<WorkflowInboxSnapshotVO>> inbox(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listInbox(status, limit));
    }

    @GetMapping("/quarantine")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询工作流隔离消息")
    public R<List<WorkflowQuarantineSnapshotVO>> quarantine(
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listQuarantine(limit));
    }

    @GetMapping("/reconcile")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "对账工作流业务与 durable 状态")
    public R<List<WorkflowRecoveryReconciliationVO>> reconcile(
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.reconcile(limit));
    }

    @GetMapping("/diagnostics")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询工作流运行诊断")
    public R<WorkflowRuntimeDiagnosticsVO> diagnostics() {
        return R.ok(recovery.diagnostics());
    }

    @PostMapping("/outbox/{eventId}/retry")
    @SysLog("人工重试工作流 Outbox")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "人工重试失败 Outbox")
    public R<WorkflowRecoveryActionVO> retryOutbox(@PathVariable String eventId,
            @RequestParam String reason) {
        return R.ok(recovery.retryOutbox(eventId, reason));
    }

    @PostMapping("/inbox/{eventId}/retry")
    @SysLog("人工重试工作流 Inbox")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "人工重试失败 Inbox")
    public R<WorkflowRecoveryActionVO> retryInbox(@PathVariable String eventId,
            @RequestParam String reason) {
        return R.ok(recovery.retryInbox(eventId, reason));
    }

    @PostMapping("/quarantine/{evidenceId}/replay")
    @SysLog("重放工作流隔离消息")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "重放工作流隔离消息")
    public R<WorkflowRecoveryReplayVO> replayQuarantine(@PathVariable String evidenceId,
            @RequestParam String reason) {
        return R.ok(recovery.replayQuarantine(evidenceId, reason));
    }
}
