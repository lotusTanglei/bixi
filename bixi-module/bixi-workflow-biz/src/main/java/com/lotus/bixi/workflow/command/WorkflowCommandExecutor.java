package com.lotus.bixi.workflow.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import com.lotus.bixi.workflow.service.impl.WorkflowAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
public class WorkflowCommandExecutor {
    public static final String TENANT_SCOPE = "default";
    private final WorkflowCommandTransaction transactions;
    private final WorkflowAccessService access;

    public record CommandInput(String requestId, String operation, String resourceId, String sourceOwner,
            Long actorId, String actorName, String tenantScope, JsonNode normalizedPayload,
            String requestHash, String terminalTaskId, String processInstanceId) { }
    public record CommandResult(String processInstanceId) { }

    /** Called only after the operation facade's current permission and input validation. */
    public <T> T execute(CommandInput input, Class<T> resultType, Supplier<T> work) {
        WorkflowRequestDTO.requireRequestId(input.requestId());
        access.requireCurrentUser(input.actorId());
        var saved = transactions.find(input.tenantScope(), input.actorId(), input.requestId());
        if (saved != null) return replay(input, saved, resultType);
        return executeNew(input, resultType, work);
    }

    /** Executes an already-authorized durable command in the caller's inbox transaction. */
    public <T> T executeTrusted(CommandInput input, Class<T> resultType, Supplier<T> work) {
        return executeTrusted(input, resultType, () -> { }, work);
    }

    /** Executes a trusted command after replay lookup and a guard that only applies to new commands. */
    public <T> T executeTrusted(CommandInput input, Class<T> resultType, Runnable newCommandGuard, Supplier<T> work) {
        WorkflowRequestDTO.requireRequestId(input.requestId());
        if (input.actorId() == null || input.actorId() <= 0 || input.tenantScope() == null
                || !input.tenantScope().matches("[1-9][0-9]{0,18}")
                || input.sourceOwner() == null || !input.sourceOwner().matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("可信流程命令身份无效");
        }
        var saved = transactions.find(input.tenantScope(), input.actorId(), input.requestId());
        if (saved != null) return replay(input, saved, resultType);
        newCommandGuard.run();
        // A request-key race aborts the whole inbox attempt. Its durable retry observes the committed winner.
        return transactions.executeJoined(input, resultType, work);
    }

    /** Resolves mutable engine identity after replay lookup and before opening the write transaction. */
    public <T> T executeWithProcessLookup(CommandInput input, Class<T> resultType,
            Supplier<String> processLookup, Function<String, T> work) {
        WorkflowRequestDTO.requireRequestId(input.requestId());
        access.requireCurrentUser(input.actorId());
        var saved = transactions.find(input.tenantScope(), input.actorId(), input.requestId());
        if (saved != null) return replay(input, saved, resultType);
        String processInstanceId;
        try {
            processInstanceId = processLookup.get();
        } catch (RuntimeException lookupFailure) {
            // A concurrent identical request can commit and remove the task after the initial replay miss.
            var winner = transactions.find(input.tenantScope(), input.actorId(), input.requestId());
            if (winner != null) return replay(input, winner, resultType);
            if (input.terminalTaskId() != null && transactions.hasSucceededTerminalTask(
                    input.tenantScope(), input.actorId(), input.terminalTaskId())) {
                throw new WorkflowOperationConflictException(input.requestId());
            }
            throw lookupFailure;
        }
        CommandInput prepared = new CommandInput(input.requestId(), input.operation(), input.resourceId(),
                input.sourceOwner(), input.actorId(), input.actorName(), input.tenantScope(), input.normalizedPayload(),
                input.requestHash(), input.terminalTaskId(), processInstanceId);
        return executeNew(prepared, resultType, () -> work.apply(processInstanceId));
    }

    private <T> T executeNew(CommandInput input, Class<T> resultType, Supplier<T> work) {
        try {
            return transactions.executeNew(input, resultType, work);
        } catch (WorkflowCommandTransaction.RequestKeyCollision | WorkflowCommandTransaction.TerminalTaskCollision collision) {
            // executeNew's proxy has rolled back and released its connection. This read has a fresh snapshot.
            var winner = transactions.find(input.tenantScope(), input.actorId(), input.requestId());
            if (winner != null) return replay(input, winner, resultType);
            if (collision instanceof WorkflowCommandTransaction.TerminalTaskCollision) {
                throw new WorkflowOperationConflictException(input.requestId());
            }
            throw collision;
        }
    }

    @HasPermission({"workflow_process_view", "workflow_task_view"})
    public WorkflowCommandVO getCommand(String requestId) {
        WorkflowRequestDTO.requireRequestId(requestId);
        var user = access.currentUser();
        if (user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new IllegalArgumentException("租户上下文缺失");
        }
        var saved = transactions.find(user.getTenantId().toString(), user.getId(), requestId);
        if (saved == null) throw new WorkflowCommandNotFoundException(requestId);
        requireSuccess(saved);
        return saved.result();
    }

    private <T> T replay(CommandInput input, WorkflowCommandTransaction.Saved saved, Class<T> resultType) {
        if (saved.hashVersion() != WorkflowRequestHasher.HASH_VERSION || !saved.requestHash().equals(input.requestHash())) {
            throw new WorkflowRequestConflictException(input.requestId());
        }
        requireSuccess(saved);
        return transactions.decode(saved.result().response(), resultType);
    }
    private void requireSuccess(WorkflowCommandTransaction.Saved saved) {
        if (!"SUCCEEDED".equals(saved.status()) || saved.result().response() == null) {
            throw new IllegalStateException("流程命令结果尚未成功提交");
        }
    }
}
