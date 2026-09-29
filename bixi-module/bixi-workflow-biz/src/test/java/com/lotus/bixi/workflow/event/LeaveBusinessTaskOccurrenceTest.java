package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationRequested;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaveBusinessTaskOccurrenceTest {

    @Test
    void repeatedActivityEntriesUseDistinctOperationIdsAndCompensationKeepsOccurrence() {
        List<DurableMessage> messages = new ArrayList<>();
        WorkflowEventCodec codec = new WorkflowEventCodec();
        WorkflowBusinessTaskEventPublisher publisher = new WorkflowBusinessTaskEventPublisher(
                (message, dedupKey, aggregateKey, aggregateSequence) -> messages.add(message), codec);
        Map<String, Object> variables = new HashMap<>();
        variables.put("businessId", 7L);
        variables.put("businessRound", 1);
        variables.put("businessKey", "leave:7:1");
        variables.put("startRequestId", UUID.randomUUID().toString());
        variables.put("startRequestHash", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        variables.put("startUserId", 11L);
        variables.put("startUserName", "applicant");
        variables.put("tenantScope", "1");
        DelegateExecution execution = execution(variables);

        LeaveBusinessTaskRequestDelegate request = new LeaveBusinessTaskRequestDelegate(publisher);
        request.execute(execution);
        WorkflowEvent first = decode(codec, messages.get(0));
        request.execute(execution);
        WorkflowEvent second = decode(codec, messages.get(1));

        WorkflowBusinessTaskRequested firstPayload = (WorkflowBusinessTaskRequested) first.payload();
        WorkflowBusinessTaskRequested secondPayload = (WorkflowBusinessTaskRequested) second.payload();
        assertThat(firstPayload.activityOccurrence()).isOne();
        assertThat(secondPayload.activityOccurrence()).isEqualTo(2);
        assertThat(secondPayload.operationId()).isNotEqualTo(firstPayload.operationId());

        new LeaveCompensationRequestDelegate(publisher).execute(execution);
        WorkflowEvent compensation = decode(codec, messages.get(2));
        WorkflowCompensationRequested compensationPayload = (WorkflowCompensationRequested) compensation.payload();
        assertThat(compensationPayload.operationId()).isEqualTo(secondPayload.operationId());
        assertThat(compensationPayload.compensationId()).isNotEqualTo(
                UUID.nameUUIDFromBytes((secondPayload.operationId() + ":compensation:1")
                        .getBytes(StandardCharsets.UTF_8)).toString());
    }

    private static DelegateExecution execution(Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getId()).thenReturn("execution-1");
        when(execution.getProcessInstanceId()).thenReturn("process-1");
        when(execution.getCurrentActivityId()).thenReturn("requestBusiness");
        when(execution.getVariable(anyString())).thenAnswer(invocation -> variables.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            variables.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(execution).setVariable(anyString(), org.mockito.ArgumentMatchers.any());
        return execution;
    }

    private static WorkflowEvent decode(WorkflowEventCodec codec, DurableMessage message) {
        return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
    }
}
