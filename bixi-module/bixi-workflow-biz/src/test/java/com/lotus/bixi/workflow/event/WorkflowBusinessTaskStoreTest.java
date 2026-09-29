package com.lotus.bixi.workflow.event;

import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowBusinessTaskStoreTest {
    private static final String PROCESS_ID = "process-7";
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String HASH = "a".repeat(64);

    private WorkflowBusinessTaskStore store;
    private WorkflowBusinessTaskEventPublisher.Context context;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:workflow-business-task;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS wf_business_task");
        jdbc.execute("""
                CREATE TABLE wf_business_task (
                  operation_id VARCHAR(36) PRIMARY KEY,
                  process_instance_id VARCHAR(64) NOT NULL,
                  execution_id VARCHAR(64) NOT NULL,
                  activity_id VARCHAR(128) NOT NULL,
                  activity_occurrence INT NOT NULL,
                  business_owner VARCHAR(64) NOT NULL,
                  business_table VARCHAR(128) NOT NULL,
                  business_id BIGINT NOT NULL,
                  business_round INT NOT NULL,
                  request_hash VARCHAR(64) NOT NULL,
                  tenant_scope VARCHAR(32) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  deadline TIMESTAMP(6) NOT NULL,
                  result_event_id VARCHAR(36),
                  compensation_id VARCHAR(36),
                  last_error VARCHAR(128),
                  created_at TIMESTAMP(6) NOT NULL,
                  updated_at TIMESTAMP(6) NOT NULL,
                  CONSTRAINT uk_wf_business_task_occurrence
                    UNIQUE (process_instance_id, activity_id, activity_occurrence),
                  CONSTRAINT uk_wf_business_task_compensation UNIQUE (compensation_id)
                )
                """);
        store = new WorkflowBusinessTaskStore(dataSource);
        context = new WorkflowBusinessTaskEventPublisher.Context(
                PROCESS_ID, "demo_leave_approval", 7L, "leave:7:1", 1,
                "22222222-2222-2222-2222-222222222222", HASH,
                "33333333-3333-3333-3333-333333333333", null,
                new WorkflowActorSnapshot(11L, "alice", "42", "upms", Instant.now()),
                "execution-7", OPERATION_ID, "requestBusiness", 1,
                Instant.now().plusSeconds(300));
        store.createWaiting(context);
    }

    @Test
    void exactResultEventCanBeRetriedAfterDurableStateWasAlreadyWritten() {
        WorkflowEvent event = resultEvent("44444444-4444-4444-4444-444444444444");
        WorkflowBusinessTaskResult result = (WorkflowBusinessTaskResult) event.payload();

        assertThat(store.recordBusinessResult(event, result)).isTrue();
        assertThat(store.recordBusinessResult(event, result)).isTrue();
        assertThat(store.find(OPERATION_ID).status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void differentResultEventWithSameOutcomeIsIgnoredAfterWinnerWasRecorded() {
        WorkflowEvent first = resultEvent("44444444-4444-4444-4444-444444444444");
        WorkflowEvent duplicate = resultEvent("55555555-5555-5555-5555-555555555555");

        assertThat(store.recordBusinessResult(first, (WorkflowBusinessTaskResult) first.payload())).isTrue();
        assertThat(store.recordBusinessResult(duplicate, (WorkflowBusinessTaskResult) duplicate.payload())).isFalse();
        assertThat(store.find(OPERATION_ID).resultEventId()).isEqualTo(first.eventId());
    }

    @Test
    void lateBusinessResultIsRecordedWithoutChangingCompensationState() {
        String compensationId = "66666666-6666-6666-6666-666666666666";
        store.markCompensating(context, compensationId, true);

        WorkflowEvent late = resultEvent("77777777-7777-7777-7777-777777777777");
        WorkflowBusinessTaskResult result = (WorkflowBusinessTaskResult) late.payload();

        assertThat(store.recordBusinessResultWithoutExecution(late, result)).isTrue();
        assertThat(store.find(OPERATION_ID).status()).isEqualTo("COMPENSATING");
        assertThat(store.find(OPERATION_ID).resultEventId()).isEqualTo(late.eventId());

        // A redelivery remains an acknowledgement, while a different late result cannot
        // overwrite the first durable evidence or reopen the workflow.
        assertThat(store.recordBusinessResultWithoutExecution(late, result)).isTrue();
        WorkflowEvent another = resultEvent("88888888-8888-8888-8888-888888888888");
        assertThat(store.recordBusinessResultWithoutExecution(another,
                (WorkflowBusinessTaskResult) another.payload())).isTrue();
        assertThat(store.find(OPERATION_ID).resultEventId()).isEqualTo(late.eventId());
    }

    @Test
    void terminalCompensationResultCanBeAcknowledgedAfterExecutionDisappears() {
        String compensationId = "99999999-9999-9999-9999-999999999999";
        store.markCompensating(context, compensationId, true);
        WorkflowEvent compensation = compensationEvent("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", compensationId);
        WorkflowCompensationResult result = (WorkflowCompensationResult) compensation.payload();
        assertThat(store.recordCompensationResult(compensation, result)).isTrue();

        assertThat(store.recordCompensationResultWithoutExecution(compensation, result)).isTrue();
        assertThat(store.find(OPERATION_ID).status()).isEqualTo("COMPENSATED");
    }

    private WorkflowEvent resultEvent(String eventId) {
        return new WorkflowEvent(eventId, WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT, 1,
                "upms", "workflow", "42", PROCESS_ID, "demo_leave_approval", "demo_leave_request",
                7L, "leave:7:1", 1, context.commandId(), 3, Instant.now(), context.correlationId(),
                context.causationId(), context.actor(),
                new WorkflowBusinessTaskResult(HASH, OPERATION_ID, true, "BOOK-7", null, Instant.now()));
    }

    private WorkflowEvent compensationEvent(String eventId, String compensationId) {
        return new WorkflowEvent(eventId, WorkflowEventType.WORKFLOW_COMPENSATION_RESULT, 1,
                "upms", "workflow", "42", PROCESS_ID, "demo_leave_approval", "demo_leave_request",
                7L, "leave:7:1", 1, context.commandId(), 5, Instant.now(), context.correlationId(),
                context.causationId(), context.actor(), new WorkflowCompensationResult(HASH, OPERATION_ID,
                        compensationId, true, null, Instant.now()));
    }
}
