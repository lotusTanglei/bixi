package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.service.UpmsRecoveryService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.WorkflowInboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowOutboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowQuarantineSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReconciliationVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReplayVO;
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

/** UPMS owner entry point for durable delivery recovery and audit. */
@RestController
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
@RequestMapping("/upms/recovery")
@Tag(name = "upms-recovery", description = "UPMS 可靠投递恢复管理")
public class UpmsRecoveryController {
    private final UpmsRecoveryService recovery;

    @GetMapping("/outbox")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询 UPMS Outbox")
    public R<List<WorkflowOutboxSnapshotVO>> outbox(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listOutbox(status, limit));
    }

    @GetMapping("/inbox")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询 UPMS Inbox")
    public R<List<WorkflowInboxSnapshotVO>> inbox(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listInbox(status, limit));
    }

    @GetMapping("/quarantine")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "查询 UPMS 隔离消息")
    public R<List<WorkflowQuarantineSnapshotVO>> quarantine(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.listQuarantine(limit));
    }

    @GetMapping("/reconcile")
    @HasPermission("workflow_recovery_view")
    @Operation(summary = "对账 UPMS 业务与 durable 状态")
    public R<List<WorkflowRecoveryReconciliationVO>> reconcile(
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(recovery.reconcile(limit));
    }

    @PostMapping("/outbox/{eventId}/retry")
    @SysLog("人工重试 UPMS Outbox")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "人工重试 UPMS Outbox")
    public R<WorkflowRecoveryActionVO> retryOutbox(@PathVariable String eventId,
            @RequestParam String reason) {
        return R.ok(recovery.retryOutbox(eventId, reason));
    }

    @PostMapping("/inbox/{eventId}/retry")
    @SysLog("人工重试 UPMS Inbox")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "人工重试 UPMS Inbox")
    public R<WorkflowRecoveryActionVO> retryInbox(@PathVariable String eventId,
            @RequestParam String reason) {
        return R.ok(recovery.retryInbox(eventId, reason));
    }

    @PostMapping("/quarantine/{evidenceId}/replay")
    @SysLog("重放 UPMS 隔离消息")
    @HasPermission("workflow_recovery_edit")
    @Operation(summary = "重放 UPMS 隔离消息")
    public R<WorkflowRecoveryReplayVO> replayQuarantine(@PathVariable String evidenceId,
            @RequestParam String reason) {
        return R.ok(recovery.replayQuarantine(evidenceId, reason));
    }
}
