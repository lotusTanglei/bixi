package com.lotus.bixi.workflow.command;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import com.lotus.bixi.workflow.service.impl.WorkflowAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowCommandExecutorTest {

    @Test
    void terminalCollisionReplaysSameRequestAfterTheFailedTransactionEnds() {
        WorkflowCommandTransaction transactions = mock(WorkflowCommandTransaction.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        WorkflowCommandExecutor executor = new WorkflowCommandExecutor(transactions, access);
        String requestId = UUID.randomUUID().toString();
        var input = input(requestId);
        var response = JsonNodeFactory.instance.objectNode().put("processInstanceId", "process-1");
        var saved = new WorkflowCommandTransaction.Saved("hash", WorkflowRequestHasher.HASH_VERSION, "SUCCEEDED",
                new WorkflowCommandVO(requestId, "COMPLETE", "task-1", "process-1", "SUCCESS", Instant.now(), response));
        var expected = new WorkflowCommandExecutor.CommandResult("process-1");
        when(transactions.find("default", 22L, requestId)).thenReturn(null, saved);
        when(transactions.executeNew(eq(input), eq(WorkflowCommandExecutor.CommandResult.class), any()))
                .thenThrow(new WorkflowCommandTransaction.TerminalTaskCollision(new DuplicateKeyException("terminal")));
        when(transactions.decode(response, WorkflowCommandExecutor.CommandResult.class)).thenReturn(expected);

        assertThat(executor.execute(input, WorkflowCommandExecutor.CommandResult.class, () -> expected))
                .isEqualTo(expected);
    }

    @Test
    void terminalCollisionWithoutSameRequestWinnerBecomesOperationConflict() {
        WorkflowCommandTransaction transactions = mock(WorkflowCommandTransaction.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        WorkflowCommandExecutor executor = new WorkflowCommandExecutor(transactions, access);
        String requestId = UUID.randomUUID().toString();
        var input = input(requestId);
        var expected = new WorkflowCommandExecutor.CommandResult("process-1");
        when(transactions.find("default", 22L, requestId)).thenReturn(null);
        when(transactions.executeNew(eq(input), eq(WorkflowCommandExecutor.CommandResult.class), any()))
                .thenThrow(new WorkflowCommandTransaction.TerminalTaskCollision(new DuplicateKeyException("terminal")));

        assertThatThrownBy(() -> executor.execute(input, WorkflowCommandExecutor.CommandResult.class, () -> expected))
                .isInstanceOfSatisfying(WorkflowOperationConflictException.class,
                        error -> assertThat(error.getRequestId()).isEqualTo(requestId));
    }

    private WorkflowCommandExecutor.CommandInput input(String requestId) {
        return new WorkflowCommandExecutor.CommandInput(requestId, "COMPLETE", "task-1", "workflow", 22L,
                "reviewer", "default", JsonNodeFactory.instance.objectNode(), "hash", "task-1", "process-1");
    }
}
