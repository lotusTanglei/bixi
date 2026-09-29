package com.lotus.bixi.workflow.service;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationRequested;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.event.LeaveBusinessTaskRequestDelegate;
import com.lotus.bixi.workflow.event.LeaveCompensationRequestDelegate;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskEventPublisher;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskResultHandler;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskStore;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Real Flowable execution for the v2 automatic-task and compensation paths. */
@SpringJUnitConfig({WorkflowApprovalIntegrationTest.Config.class, WorkflowLeaveBusinessTaskIntegrationTest.Config.class})
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {
        "workflow.enabled=true", "workflow.database-schema-update=true",
        "workflow.async-executor-activate=false", "flowable.check-process-definitions=false"})
class WorkflowLeaveBusinessTaskIntegrationTest {
    @Autowired ProcessEngine engine;
    @Autowired ManagementService management;
    @Autowired TrustedProcessStarter trustedProcesses;
    @Autowired ProcessDefinitionService definitions;
    @Autowired WorkflowEventCodec codec;
    @Autowired OutboxCapture outbox;
    @Autowired DataSource source;
    @Autowired WorkflowBusinessTaskStore businessTasks;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        WorkflowTestSchema.create(jdbc, "wf_process_definition", "wf_process_instance", "wf_command",
                "wf_approval_record", "wf_form_data");
        createBusinessTaskTable();
        outbox.messages.clear();
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml").deploy();
    }

    @AfterEach
    void cleanup() {
        engine.getRepositoryService().createDeploymentQuery().list()
                .forEach(deployment -> engine.getRepositoryService().deleteDeployment(deployment.getId(), true));
    }

    @Test
    void approvedBookingResultResumesReceiveTaskAndCompletesProcess() {
        var started = start();
        String taskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult().getId();

        engine.getTaskService().complete(taskId);

        DurableMessage requestMessage = pending("WORKFLOW_BUSINESS_TASK_REQUESTED");
        WorkflowEvent request = codec.decode(requestMessage.payloadJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(engine.getRuntimeService().createExecutionQuery()
                .processInstanceId(started.getProcessInstanceId()).activityId("waitBusinessResult").count()).isEqualTo(1);

        WorkflowBusinessTaskRequested payload = (WorkflowBusinessTaskRequested) request.payload();
        assertThat(businessTasks.find(payload.operationId()).status()).isEqualTo("WAITING");
        assertThat(engine.getRuntimeService().getVariable(started.getProcessInstanceId(), "businessOperationId"))
                .isEqualTo(payload.operationId());
        new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(resultMessage(request,
                new WorkflowBusinessTaskResult(payload.requestHash(), payload.operationId(), true,
                        "BOOK-" + payload.operationId().substring(0, 8), null, Instant.now())));

        assertThat(businessTasks.find(payload.operationId()).status()).isEqualTo("SUCCEEDED");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(started.getProcessInstanceId()).count()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId())).isEqualTo("completed");
    }

    @Test
    void failedBookingResultPublishesCompensationAndTerminateEndIsRecorded() {
        var started = start();
        String taskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult().getId();
        engine.getTaskService().complete(taskId);

        WorkflowEvent request = codec.decode(pending("WORKFLOW_BUSINESS_TASK_REQUESTED").payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowBusinessTaskRequested payload = (WorkflowBusinessTaskRequested) request.payload();
        assertThat(engine.getRuntimeService().getVariable(started.getProcessInstanceId(), "businessOperationId"))
                .isEqualTo(payload.operationId());
        new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(resultMessage(request,
                new WorkflowBusinessTaskResult(payload.requestHash(), payload.operationId(), false, null,
                        "BOOKING_CONFLICT", Instant.now())));

        DurableMessage compensationMessage = pending("WORKFLOW_COMPENSATION_REQUESTED");
        WorkflowEvent compensation = codec.decode(compensationMessage.payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowCompensationRequested compensationPayload = (WorkflowCompensationRequested) compensation.payload();
        assertThat(businessTasks.find(payload.operationId()).status()).isEqualTo("COMPENSATING");
        new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(resultMessage(compensation,
                new WorkflowCompensationResult(compensationPayload.requestHash(), compensationPayload.operationId(),
                        compensationPayload.compensationId(), true, null, Instant.now())));

        assertThat(businessTasks.find(payload.operationId()).status()).isEqualTo("COMPENSATED");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(started.getProcessInstanceId()).count()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId())).isEqualTo("terminated");

    }

    @Test
    void realTimerTimeoutRequestsCompensationAndLateBookingResultIsIgnored() {
        var started = start();
        String taskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult().getId();
        engine.getTaskService().complete(taskId);

        var timer = management.createTimerJobQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult();
        assertThat(timer).as("v2 receive task must create a persistent timeout job").isNotNull();
        var executable = management.moveTimerToExecutableJob(timer.getId());
        management.executeJob(executable.getId());

        WorkflowEvent request = codec.decode(pending("WORKFLOW_BUSINESS_TASK_REQUESTED").payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowBusinessTaskRequested requestPayload = (WorkflowBusinessTaskRequested) request.payload();
        assertThat(businessTasks.find(requestPayload.operationId()).status()).isEqualTo("COMPENSATING");
        assertThat(businessTasks.find(requestPayload.operationId()).lastError()).isEqualTo("AUTO_TASK_TIMEOUT");
        assertThat(engine.getRuntimeService().createExecutionQuery()
                .processInstanceId(started.getProcessInstanceId()).activityId("waitCompensationResult").count())
                .isOne();

        var lateResult = resultMessage(request, new WorkflowBusinessTaskResult(
                requestPayload.requestHash(), requestPayload.operationId(), true,
                "BOOK-LATE", null, Instant.now()));
        assertThat(new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(lateResult))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.IGNORED);
        assertThat(businessTasks.find(requestPayload.operationId()).resultEventId())
                .isEqualTo(codec.decode(lateResult.payloadJson().getBytes(StandardCharsets.UTF_8)).eventId());

        WorkflowEvent compensation = codec.decode(pending("WORKFLOW_COMPENSATION_REQUESTED").payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowCompensationRequested compensationPayload = (WorkflowCompensationRequested) compensation.payload();
        var compensationResult = resultMessage(compensation, new WorkflowCompensationResult(
                compensationPayload.requestHash(), compensationPayload.operationId(),
                compensationPayload.compensationId(), true, null, Instant.now()));
        assertThat(new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(compensationResult))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        assertThat(businessTasks.find(requestPayload.operationId()).status()).isEqualTo("COMPENSATED");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(started.getProcessInstanceId()).count()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId())).isEqualTo("terminated");
        assertThat(new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec,
                businessTasks).handle(lateResult))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.IGNORED);
    }

    @Test
    void oldDefinitionCompletesAfterExplicitV3DeploymentAndNewDefinitionUsesAsyncBoundary() {
        var oldInstance = start();
        String oldDefinitionId = oldInstance.getProcessDefinitionId();
        int oldVersion = engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionId(oldDefinitionId).singleResult().getVersion();

        var v3 = definitions.deployDemoV3();
        assertThat(v3.getVersion()).isGreaterThan(oldVersion);
        assertThat(engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionId(oldDefinitionId).singleResult()).isNotNull();
        assertThat(engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("demo_leave_approval").latestVersion().singleResult().getId())
                .isEqualTo(v3.getProcessDefinitionId());

        String oldTaskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(oldInstance.getProcessInstanceId()).singleResult().getId();
        engine.getTaskService().complete(oldTaskId);
        WorkflowEvent oldRequest = codec.decode(pending("WORKFLOW_BUSINESS_TASK_REQUESTED").payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowBusinessTaskRequested oldPayload = (WorkflowBusinessTaskRequested) oldRequest.payload();
        new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec, businessTasks)
                .handle(resultMessage(oldRequest, new WorkflowBusinessTaskResult(oldPayload.requestHash(),
                        oldPayload.operationId(), true, "BOOK-OLD", null, Instant.now())));
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(oldInstance.getProcessInstanceId()).count()).isZero();

        outbox.messages.clear();
        var newInstance = start(102L);
        assertThat(newInstance.getProcessDefinitionId()).isEqualTo(v3.getProcessDefinitionId());
        String newTaskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(newInstance.getProcessInstanceId()).singleResult().getId();
        engine.getTaskService().complete(newTaskId);
        var asyncJob = management.createJobQuery().processInstanceId(newInstance.getProcessInstanceId()).singleResult();
        assertThat(asyncJob).as("v3 business request must cross an explicit async boundary").isNotNull();
        management.executeJob(asyncJob.getId());
        WorkflowEvent newRequest = codec.decode(pending("WORKFLOW_BUSINESS_TASK_REQUESTED").payloadJson()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowBusinessTaskRequested newPayload = (WorkflowBusinessTaskRequested) newRequest.payload();
        new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec, businessTasks)
                .handle(resultMessage(newRequest, new WorkflowBusinessTaskResult(newPayload.requestHash(),
                        newPayload.operationId(), true, "BOOK-NEW", null, Instant.now())));
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(newInstance.getProcessInstanceId()).count()).isZero();
    }

    private com.lotus.bixi.workflow.api.vo.ProcessInstanceVO start() {
        return start(101L);
    }

    private com.lotus.bixi.workflow.api.vo.ProcessInstanceVO start(long businessId) {
        WorkflowApprovalIntegrationTest.login(11L);
        return WorkflowTrustedStartTestSupport.start(trustedProcesses, "Leave request", businessId, 1);
    }

    private DurableMessage resultMessage(WorkflowEvent request, Object payload) {
        WorkflowEvent result = new WorkflowEvent(UUID.randomUUID().toString(),
                payload instanceof WorkflowBusinessTaskResult ? WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT
                        : WorkflowEventType.WORKFLOW_COMPENSATION_RESULT,
                1, "upms", "workflow", request.tenantScope(), request.processInstanceId(), request.processKey(),
                request.businessTable(), request.businessId(), request.businessKey(), request.round(),
                request.commandId(), request.aggregateSequence() + 1, Instant.now(), request.correlationId(),
                request.eventId(), request.actor(), (com.lotus.bixi.workflow.api.event.WorkflowPayload) payload);
        return DurableMessage.create("upms", "workflow", result.eventId(), result.type().name(), 1,
                new String(codec.encode(result), java.nio.charset.StandardCharsets.UTF_8));
    }

    private DurableMessage pending(String type) {
        return outbox.messages.stream()
                .filter(message -> type.equals(message.type()))
                .findFirst().orElseThrow();
    }

    private void createBusinessTaskTable() {
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
    }

    static final class OutboxCapture {
        final List<DurableMessage> messages = new ArrayList<>();
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean("workflowEventCodec") WorkflowEventCodec workflowEventCodec() { return new WorkflowEventCodec(); }

        @Bean OutboxCapture outboxCapture() { return new OutboxCapture(); }

        @Bean("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore(OutboxCapture capture) {
            JdbcOutboxStore store = mock(JdbcOutboxStore.class);
            doAnswer(invocation -> {
                capture.messages.add(invocation.getArgument(0, DurableMessage.class));
                return null;
            }).when(store).enqueue(any(), any(), any(), any());
            return store;
        }

        @Bean WorkflowBusinessTaskEventPublisher workflowBusinessTaskEventPublisher(
                JdbcOutboxStore outbox, WorkflowEventCodec codec) {
            return new WorkflowBusinessTaskEventPublisher(outbox, codec);
        }

        @Bean WorkflowBusinessTaskStore workflowBusinessTaskStore(DataSource source) {
            return new WorkflowBusinessTaskStore(source);
        }

        @Bean("leaveBusinessTaskRequestDelegate") LeaveBusinessTaskRequestDelegate leaveBusinessTaskRequestDelegate(
                WorkflowBusinessTaskEventPublisher publisher, WorkflowBusinessTaskStore tasks) {
            return new LeaveBusinessTaskRequestDelegate(publisher, tasks);
        }

        @Bean("leaveCompensationRequestDelegate") LeaveCompensationRequestDelegate leaveCompensationRequestDelegate(
                WorkflowBusinessTaskEventPublisher publisher, WorkflowBusinessTaskStore tasks) {
            return new LeaveCompensationRequestDelegate(publisher, tasks);
        }
    }
}
