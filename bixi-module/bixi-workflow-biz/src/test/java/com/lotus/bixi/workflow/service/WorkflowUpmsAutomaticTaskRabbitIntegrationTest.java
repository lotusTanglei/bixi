package com.lotus.bixi.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.OutboxDispatcher;
import com.lotus.bixi.common.mq.reliable.RabbitDurableTransport;
import com.lotus.bixi.common.mq.reliable.RabbitOwnerEndpoint;
import com.lotus.bixi.common.mq.reliable.ReliableDeliveryProperties;
import com.lotus.bixi.common.mq.reliable.ReliableRabbitProperties;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.event.LeaveBusinessTaskEventHandler;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventHandler;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventPublisher;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.upms.demo.leave.service.LeaveBookingService;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.event.LeaveBusinessTaskRequestDelegate;
import com.lotus.bixi.workflow.event.LeaveCompensationRequestDelegate;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskEventPublisher;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskResultHandler;
import com.lotus.bixi.workflow.event.WorkflowEventRecorder;
import com.lotus.bixi.workflow.event.WorkflowStartRequestedHandler;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real MySQL/Rabbit proof for the automatic leave-task business loop. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
@SpringJUnitConfig({WorkflowApprovalIntegrationTest.Config.class,
        WorkflowUpmsAutomaticTaskRabbitIntegrationTest.Config.class})
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {
        "workflow.enabled=true", "workflow.database-schema-update=true",
        "workflow.async-executor-activate=false", "flowable.check-process-definitions=false",
        "bixi.deployment.mode=cloud"})
class WorkflowUpmsAutomaticTaskRabbitIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired ProcessEngine engine;
    @Autowired ProcessInstanceService processes;
    @Autowired WfTaskService tasks;
    @Autowired LeaveWorkflowEventPublisher startPublisher;
    @Autowired @Qualifier("workflowOutboxStore") JdbcOutboxStore workflowOutbox;
    @Autowired @Qualifier("upmsOutboxStore") JdbcOutboxStore upmsOutbox;
    @Autowired @Qualifier("workflowInboxStore") JdbcInboxStore workflowInbox;
    @Autowired @Qualifier("upmsInboxStore") JdbcInboxStore upmsInbox;
    @Autowired InboxExecutor workflowInboxExecutor;
    @Autowired @Qualifier("upmsInboxExecutor") InboxExecutor upmsInboxExecutor;
    @Autowired @Qualifier("workflowQuarantine") JdbcQuarantineStore workflowQuarantine;
    @Autowired @Qualifier("upmsQuarantineStore") JdbcQuarantineStore upmsQuarantine;
    @Autowired ReliableDeliveryProperties deliveryProperties;
    @Autowired DataSource source;
    @Autowired DataSourceTransactionManager transactionManager;

    private JdbcTemplate jdbc;
    private RabbitOwnerEndpoint workflowEndpoint;
    private RabbitOwnerEndpoint upmsEndpoint;
    private OutboxDispatcher workflowDispatcher;
    private OutboxDispatcher upmsDispatcher;

    @BeforeEach
    void setup() throws Exception {
        assumeTrue(System.getenv("RELIABLE_RABBIT_TEST_HOST") != null);
        jdbc = new JdbcTemplate(source);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record",
                "wf_form_data", "demo_leave_request", "demo_leave_booking");
        applyReliableSchema("20260921_reliable_outbox.sql");
        applyReliableSchema("20260921_reliable_inbox.sql");
        applyReliableSchema("20260921_reliable_quarantine.sql");
        jdbc.update("TRUNCATE TABLE reliable_outbox");
        jdbc.update("TRUNCATE TABLE reliable_inbox");
        jdbc.update("TRUNCATE TABLE reliable_quarantine");
        jdbc.update("""
                INSERT INTO demo_leave_request
                    (id, applicant_id, approver_id, start_date, end_date, reason, leave_status,
                     business_key, round, del_flag, status, data_status, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTING', ?, 1, '0', '0', '0', 1)
                """, 101L, 11L, 22L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
                "真实 Rabbit 自动任务链路", "demo_leave:101:1");
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml").deploy();
        WorkflowApprovalIntegrationTest.login(11L);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Topology topology = topology(suffix);
        workflowEndpoint = new RabbitOwnerEndpoint(topology.settings(),
                new RabbitDurableTransport.Route("workflow", "upms", topology.workflowExchange(), "upms.workflow"),
                new RabbitDurableTransport.Route("upms", "workflow", topology.upmsExchange(), "workflow.upms"),
                topology.workflowQueue(), workflowInboxExecutor, workflowInbox, workflowQuarantine,
                deliveryProperties, () -> true);
        upmsEndpoint = new RabbitOwnerEndpoint(topology.settings(),
                new RabbitDurableTransport.Route("upms", "workflow", topology.upmsExchange(), "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", topology.workflowExchange(), "upms.workflow"),
                topology.upmsQueue(), upmsInboxExecutor, upmsInbox, upmsQuarantine,
                deliveryProperties, () -> true);
        workflowDispatcher = new OutboxDispatcher(workflowOutbox, "workflow", workflowEndpoint, deliveryProperties);
        upmsDispatcher = new OutboxDispatcher(upmsOutbox, "upms", upmsEndpoint, deliveryProperties);
        workflowEndpoint.start();
        upmsEndpoint.start();
    }

    @AfterEach
    void cleanup() {
        if (workflowDispatcher != null) workflowDispatcher.close();
        if (upmsDispatcher != null) upmsDispatcher.close();
        if (upmsEndpoint != null) upmsEndpoint.stop();
        if (workflowEndpoint != null) workflowEndpoint.stop();
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        engine.getRepositoryService().createDeploymentQuery().list()
                .forEach(deployment -> engine.getRepositoryService().deleteDeployment(deployment.getId(), true));
    }

    @Test
    void realRabbitLoopPersistsEveryOwnerBoundaryAndDeduplicatesAutomaticTask() throws Exception {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(101L);
        leave.setApplicantId(11L);
        leave.setApproverId(22L);
        leave.setStartDate(LocalDate.of(2026, 10, 1));
        leave.setEndDate(LocalDate.of(2026, 10, 2));
        leave.setReason("真实 Rabbit 自动任务链路");
        leave.setRound(1);
        leave.setBusinessKey("demo_leave:101:1");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        LeaveWorkflowEventPublisher.Published published = transaction.execute(status ->
                startPublisher.publishStart(leave, actor(11L)));
        assertThat(published).isNotNull();

        await("workflow receives UPMS start request", () -> "PROCESSED".equals(
                status("workflow", "upms", "WORKFLOW_START_REQUESTED")), 12);
        await("workflow started event is processed", () -> "PROCESSED".equals(
                status("upms", "workflow", "WORKFLOW_STARTED")), 12);
        String processId = awaitValue("leave process identity", () -> jdbc.queryForObject(
                "SELECT process_instance_id FROM demo_leave_request WHERE id = 101", String.class), 12);
        assertThat(processId).isNotBlank();

        WorkflowApprovalIntegrationTest.login(22L);
        String taskId = awaitValue("human approval task", () -> {
            var task = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            return task == null ? null : task.getId();
        }, 12);
        var complete = new com.lotus.bixi.workflow.api.dto.TaskCompleteDTO();
        complete.setRequestId(UUID.randomUUID().toString());
        complete.setTaskId(taskId);
        complete.setApprovalComment("同意");
        tasks.complete(complete);

        // Keep the UPMS consumer offline while the request is sent. The Rabbit queue
        // retains the delivery, while a one-shot DB failure after transport success
        // leaves the source outbox IN_FLIGHT for the recovery replay.
        upmsEndpoint.stop();
        DurableMessage taskRequest = load("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        jdbc.execute("""
                CREATE TRIGGER workflow_task_mark_delivered_failure
                BEFORE UPDATE ON reliable_outbox FOR EACH ROW
                BEGIN
                    IF NEW.source_owner = 'workflow' AND NEW.event_id = '%s'
                            AND NEW.status = 'DELIVERED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'simulated mark-delivered failure';
                    END IF;
                END
                """.formatted(taskRequest.eventId()));
        try {
            assertThatThrownBy(workflowDispatcher::dispatchOnce).isInstanceOf(DataAccessException.class);
        }
        finally {
            jdbc.execute("DROP TRIGGER workflow_task_mark_delivered_failure");
        }
        assertThat(status("upms", "workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED")).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_outbox WHERE source_owner=? AND event_id=?",
                String.class, "workflow", taskRequest.eventId())).isEqualTo("IN_FLIGHT");
        jdbc.update("UPDATE reliable_outbox SET lease_until=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) "
                + "WHERE source_owner=? AND event_id=?", "workflow", taskRequest.eventId());
        upmsEndpoint.start();
        assertThat(workflowDispatcher.dispatchOnce()).isOne();

        await("automatic task result completes leave", () -> "APPROVED".equals(
                jdbc.queryForObject("SELECT leave_status FROM demo_leave_request WHERE id = 101", String.class)), 20);
        assertThat(jdbc.queryForObject("SELECT booking_state FROM demo_leave_booking WHERE leave_id = 101",
                String.class)).isEqualTo("BOOKED");
        assertThat(processes.getById(processId).getStatus()).isEqualTo("completed");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).count())
                .isZero();

        assertDelivered("upms", "WORKFLOW_START_REQUESTED");
        assertDelivered("workflow", "WORKFLOW_STARTED");
        assertDelivered("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertDelivered("upms", "WORKFLOW_BUSINESS_TASK_RESULT");
        assertDelivered("workflow", "WORKFLOW_COMPLETED");
        assertProcessed("workflow", "WORKFLOW_START_REQUESTED");
        assertProcessed("upms", "WORKFLOW_STARTED");
        assertProcessed("upms", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertProcessed("workflow", "WORKFLOW_BUSINESS_TASK_RESULT");
        assertProcessed("upms", "WORKFLOW_COMPLETED");

        DurableMessage duplicateTaskRequest = load("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        workflowEndpoint.deliver(duplicateTaskRequest);
        await("duplicate automatic task is acknowledged", () -> "PROCESSED".equals(
                status("upms", "workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED")), 12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id = 101",
                Integer.class)).isOne();
    }

    @Test
    void realRabbitTimerTimeoutIgnoresLateBookingAndConvergesCompensation() throws Exception {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(101L);
        leave.setApplicantId(11L);
        leave.setApproverId(22L);
        leave.setStartDate(LocalDate.of(2026, 10, 1));
        leave.setEndDate(LocalDate.of(2026, 10, 2));
        leave.setReason("真实 Rabbit 超时补偿链路");
        leave.setRound(1);
        leave.setBusinessKey("demo_leave:101:1");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        LeaveWorkflowEventPublisher.Published published = transaction.execute(
                status -> startPublisher.publishStart(leave, actor(11L)));
        assertThat(published).isNotNull();
        await("workflow receives UPMS start request", () -> "PROCESSED".equals(
                status("workflow", "upms", "WORKFLOW_START_REQUESTED")), 12);
        await("workflow started event is processed", () -> "PROCESSED".equals(
                status("upms", "workflow", "WORKFLOW_STARTED")), 12);
        String processId = awaitValue("leave process identity", () -> jdbc.queryForObject(
                "SELECT process_instance_id FROM demo_leave_request WHERE id = 101", String.class), 12);

        WorkflowApprovalIntegrationTest.login(22L);
        String taskId = awaitValue("human approval task", () -> {
            var task = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            return task == null ? null : task.getId();
        }, 12);
        var complete = new com.lotus.bixi.workflow.api.dto.TaskCompleteDTO();
        complete.setRequestId(UUID.randomUUID().toString());
        complete.setTaskId(taskId);
        complete.setApprovalComment("同意");
        tasks.complete(complete);

        upmsEndpoint.stop();
        DurableMessage taskRequest = load("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertThat(workflowDispatcher.dispatchOnce()).isOne();
        assertThat(status("upms", "workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED")).isNull();

        var timer = engine.getManagementService().createTimerJobQuery()
                .processInstanceId(processId).singleResult();
        assertThat(timer).as("automatic task timeout must be durable").isNotNull();
        var executable = engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        engine.getManagementService().executeJob(executable.getId());
        assertThat(engine.getRuntimeService().createExecutionQuery()
                .processInstanceId(processId).activityId("waitCompensationResult").count()).isOne();

        DurableMessage compensationRequest = load("workflow", "WORKFLOW_COMPENSATION_REQUESTED");
        assertThat(compensationRequest.eventId()).isNotEqualTo(taskRequest.eventId());
        assertThat(workflowDispatcher.dispatchOnce()).isOne();
        upmsEndpoint.start();

        await("late booking and compensation converge to canceled", () -> "CANCELED".equals(
                jdbc.queryForObject("SELECT leave_status FROM demo_leave_request WHERE id = 101", String.class)), 20);
        await("late booking result is ignored by workflow", () -> "IGNORED".equals(
                status("workflow", "upms", "WORKFLOW_BUSINESS_TASK_RESULT")), 20);
        assertThat(jdbc.queryForObject("SELECT booking_state FROM demo_leave_booking WHERE leave_id = 101",
                String.class)).isEqualTo("CANCELED");
        assertThat(processes.getById(processId).getStatus()).isEqualTo("terminated");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).count())
                .isZero();
        assertProcessed("upms", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertProcessed("upms", "WORKFLOW_COMPENSATION_REQUESTED");
        assertThat(status("workflow", "upms", "WORKFLOW_COMPENSATION_RESULT")).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id = 101",
                Integer.class)).isOne();
    }

    @Test
    void realRabbitBookingAndCompensationRaceConvergesToOneCancellation() throws Exception {
        LeaveWorkflowEventPublisher.Published published = new TransactionTemplate(transactionManager).execute(
                status -> startPublisher.publishStart(leave(), actor(11L)));
        assertThat(published).isNotNull();
        await("workflow receives start request", () -> "PROCESSED".equals(
                status("workflow", "upms", "WORKFLOW_START_REQUESTED")), 12);
        await("workflow started event is processed", () -> "PROCESSED".equals(
                status("upms", "workflow", "WORKFLOW_STARTED")), 12);
        String processId = awaitValue("leave process identity", () -> jdbc.queryForObject(
                "SELECT process_instance_id FROM demo_leave_request WHERE id = 101", String.class), 12);

        WorkflowApprovalIntegrationTest.login(22L);
        String taskId = awaitValue("human approval task", () -> {
            var task = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            return task == null ? null : task.getId();
        }, 12);
        var complete = new com.lotus.bixi.workflow.api.dto.TaskCompleteDTO();
        complete.setRequestId(UUID.randomUUID().toString());
        complete.setTaskId(taskId);
        complete.setApprovalComment("同意");
        tasks.complete(complete);

        // Pause the actual consumer after both messages have been durably routed. The two
        // owner-local handlers below then run in separate transactions at the same time.
        upmsEndpoint.stop();
        DurableMessage bookingRequest = load("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertThat(workflowDispatcher.dispatchOnce()).isOne();
        var timer = engine.getManagementService().createTimerJobQuery()
                .processInstanceId(processId).singleResult();
        assertThat(timer).as("automatic task timeout must be durable").isNotNull();
        var executable = engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        engine.getManagementService().executeJob(executable.getId());
        DurableMessage compensationRequest = load("workflow", "WORKFLOW_COMPENSATION_REQUESTED");
        assertThat(workflowDispatcher.dispatchOnce()).isOne();

        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var booking = workers.submit(() -> {
                ready.countDown();
                awaitLatch(go);
                return upmsInboxExecutor.receive(bookingRequest);
            });
            var compensation = workers.submit(() -> {
                ready.countDown();
                awaitLatch(go);
                return upmsInboxExecutor.receive(compensationRequest);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(booking.get(15, TimeUnit.SECONDS)).isEqualTo(
                    com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
            assertThat(compensation.get(15, TimeUnit.SECONDS)).isEqualTo(
                    com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        }
        finally {
            go.countDown();
            workers.shutdownNow();
        }

        // Reopen the Rabbit consumer so the outgoing result events traverse the same wire
        // protocol and reach Workflow in whichever order the database race produced.
        upmsEndpoint.start();
        await("booking/compensation race converges", () -> "CANCELED".equals(
                jdbc.queryForObject("SELECT leave_status FROM demo_leave_request WHERE id = 101", String.class)), 20);
        assertThat(jdbc.queryForObject("SELECT booking_state FROM demo_leave_booking WHERE leave_id = 101",
                String.class)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id = 101",
                Integer.class)).isOne();
        assertThat(processes.getById(processId).getStatus()).isEqualTo("terminated");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).count())
                .isZero();
    }

    @Test
    void realRabbitRejectsUnknownSchemaAndConflictingPayloadWithoutChangingProcessedInbox() throws Exception {
        LeaveWorkflowEventPublisher.Published published = new TransactionTemplate(transactionManager).execute(
                status -> startPublisher.publishStart(leave(), actor(11L)));
        assertThat(published).isNotNull();
        await("workflow receives start request", () -> "PROCESSED".equals(
                status("workflow", "upms", "WORKFLOW_START_REQUESTED")), 12);

        DurableMessage original = load("upms", "WORKFLOW_START_REQUESTED");
        DurableMessage conflicting = withPayloadField(original, "title", "冲突载荷");
        upmsEndpoint.deliver(conflicting);
        await("conflicting event is quarantined", () -> quarantineReason(workflowQuarantine,
                original.eventId()) != null, 12);
        assertThat(quarantineReason(workflowQuarantine, original.eventId())).isEqualTo("CONFLICT");
        assertThat(status("workflow", "upms", "WORKFLOW_START_REQUESTED")).isEqualTo("PROCESSED");

        ObjectNode unknownVersion = (ObjectNode) JSON.readTree(original.payloadJson());
        unknownVersion.put("schemaVersion", 2);
        DurableMessage unknown = DurableMessage.create("upms", "workflow", UUID.randomUUID().toString(),
                original.type(), 2, JSON.writeValueAsString(unknownVersion));
        upmsEndpoint.deliver(unknown);
        await("unknown schema is quarantined", () -> quarantineReason(workflowQuarantine,
                unknown.eventId()) != null, 12);
        assertThat(quarantineReason(workflowQuarantine, unknown.eventId())).isEqualTo("PERMANENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_inbox WHERE target_owner='workflow' "
                + "AND event_id=?", Integer.class, unknown.eventId())).isZero();
    }

    @Test
    void realRabbitIgnoresAutomaticTaskFromAnOlderLeaveRound() throws Exception {
        jdbc.update("UPDATE demo_leave_request SET round=2, process_instance_id=?, leave_status='IN_REVIEW', "
                + "start_request_hash=? WHERE id=101", "old-process-101", "a".repeat(64));
        String operationId = UUID.randomUUID().toString();
        WorkflowEvent event = new WorkflowEvent(UUID.randomUUID().toString(),
                WorkflowEventType.WORKFLOW_BUSINESS_TASK_REQUESTED, 1, "workflow", "upms", "default",
                "old-process-101", "demo_leave_approval", "demo_leave_request", 101L,
                "demo_leave:101:1", 1, UUID.randomUUID().toString(), 2, Instant.now(), UUID.randomUUID().toString(),
                null, new WorkflowActorSnapshot(11L, "user-11", "default", "upms", Instant.now()),
                new WorkflowBusinessTaskRequested("a".repeat(64), operationId, "old-execution-101",
                        "bookLeave", 1, Instant.now().plusSeconds(60)));
        DurableMessage message = DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(),
                1, new String(new WorkflowEventCodec().encode(event), StandardCharsets.UTF_8));

        workflowEndpoint.deliver(message);
        await("old round is ignored", () -> "IGNORED".equals(
                status("upms", "workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED")), 12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id=101",
                Integer.class)).isZero();
    }

    private void assertDelivered(String owner, String type) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox WHERE source_owner=? AND type=? AND status='DELIVERED'",
                Integer.class, owner, type)).as(owner + " " + type).isOne();
    }

    private void assertProcessed(String owner, String type) {
        String source = "workflow".equals(owner) ? "upms" : "workflow";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_inbox WHERE target_owner=? AND source_owner=? AND type=? AND status='PROCESSED'",
                Integer.class, owner, source, type)).as(owner + " " + type).isOne();
    }

    private String status(String targetOwner, String sourceOwner, String type) {
        return jdbc.query("SELECT status FROM reliable_inbox WHERE target_owner=? AND source_owner=? AND type=? ORDER BY received_at DESC LIMIT 1",
                (row, index) -> row.getString("status"), targetOwner, sourceOwner, type).stream()
                .findFirst().orElse(null);
    }

    private DurableMessage load(String sourceOwner, String type) {
        return jdbc.queryForObject("SELECT source_owner, target_owner, event_id, type, schema_version, payload_json, payload_hash FROM reliable_outbox WHERE source_owner=? AND type=? ORDER BY created_at DESC LIMIT 1",
                (row, index) -> new DurableMessage(row.getString("source_owner"), row.getString("target_owner"),
                        row.getString("event_id"), row.getString("type"), row.getInt("schema_version"),
                        row.getString("payload_json"), row.getString("payload_hash")), sourceOwner, type);
    }

    private DurableMessage withPayloadField(DurableMessage original, String field, String value) throws Exception {
        ObjectNode payload = (ObjectNode) JSON.readTree(original.payloadJson());
        ((ObjectNode) payload.get("payload")).put(field, value);
        return DurableMessage.create(original.sourceOwner(), original.targetOwner(), original.eventId(),
                original.type(), original.schemaVersion(), JSON.writeValueAsString(payload));
    }

    private String quarantineReason(JdbcQuarantineStore quarantine, String evidenceId) {
        JdbcQuarantineStore.Snapshot snapshot = quarantine.find(evidenceId);
        return snapshot == null ? null : snapshot.reason();
    }

    private LeaveRequest leave() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(101L);
        leave.setApplicantId(11L);
        leave.setApproverId(22L);
        leave.setStartDate(LocalDate.of(2026, 10, 1));
        leave.setEndDate(LocalDate.of(2026, 10, 2));
        leave.setReason("真实 Rabbit 矩阵");
        leave.setRound(1);
        leave.setBusinessKey("demo_leave:101:1");
        return leave;
    }

    private void await(String description, BooleanSupplier condition, int seconds) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        while (System.nanoTime() < deadline) {
            workflowDispatcher.dispatchOnce();
            upmsDispatcher.dispatchOnce();
            if (condition.getAsBoolean()) return;
            Thread.sleep(75);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("race start latch timed out");
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("race start latch interrupted", ex);
        }
    }

    private <T> T awaitValue(String description, ValueSupplier<T> supplier, int seconds) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        T value;
        while (System.nanoTime() < deadline) {
            workflowDispatcher.dispatchOnce();
            upmsDispatcher.dispatchOnce();
            value = supplier.get();
            if (value != null) return value;
            Thread.sleep(75);
        }
        value = supplier.get();
        assertThat(value).as(description).isNotNull();
        return value;
    }

    private BixiUser actor(long id) {
        return new BixiUser(id, 1L, 1L, "user-" + id, "unused", null,
                true, true, true, true, List.of());
    }

    private void applyReliableSchema(String file) {
        Path base = Path.of(System.getProperty("outbox.test.schema",
                "../../bixi-project-documents/sql/migrations/20260921_reliable_outbox.sql"));
        new ResourceDatabasePopulator(new FileSystemResource(base.resolveSibling(file))).execute(source);
    }

    private static Topology topology(String suffix) {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        int port = Integer.parseInt(System.getenv("RELIABLE_RABBIT_TEST_PORT"));
        var settings = new ReliableRabbitProperties.Settings(host, port,
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"), env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        return new Topology(settings, "bixi.workflow.biz." + suffix, "bixi.upms.biz." + suffix,
                "bixi.workflow.inbox.biz." + suffix, "bixi.upms.inbox.biz." + suffix);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    interface ValueSupplier<T> { T get(); }

    record Topology(ReliableRabbitProperties.Settings settings, String workflowExchange, String upmsExchange,
                    String workflowQueue, String upmsQueue) { }

    @Configuration(proxyBeanMethods = false)
    @MapperScan("com.lotus.bixi.upms.demo.leave.mapper")
    @Import(MybatisAutoConfiguration.class)
    @ImportAutoConfiguration({MybatisPlusAutoConfiguration.class})
    static class Config {
        @Bean ReliableDeliveryProperties reliableDeliveryProperties() {
            return new ReliableDeliveryProperties(Duration.ofMillis(75), Duration.ofSeconds(30),
                    Duration.ofSeconds(5), 20, 12);
        }

        @Bean("workflowEventCodec") WorkflowEventCodec workflowEventCodec() { return new WorkflowEventCodec(); }

        @Bean("leaveWorkflowEventCodec") WorkflowEventCodec leaveWorkflowEventCodec() {
            return new WorkflowEventCodec();
        }

        @Bean("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore(DataSource source,
                DataSourceTransactionManager manager, ReliableDeliveryProperties properties) {
            return new JdbcOutboxStore(source, manager, properties);
        }

        @Bean("upmsOutboxStore") JdbcOutboxStore upmsOutboxStore(DataSource source,
                DataSourceTransactionManager manager, ReliableDeliveryProperties properties) {
            return new JdbcOutboxStore(source, manager, properties);
        }

        @Bean JdbcInboxStore workflowInboxStore(DataSource source, DataSourceTransactionManager manager,
                ReliableDeliveryProperties properties) {
            return new JdbcInboxStore(source, manager, properties);
        }

        @Bean(name = "upmsInboxStore") JdbcInboxStore upmsInboxStore(DataSource source,
                DataSourceTransactionManager manager, ReliableDeliveryProperties properties) {
            return new JdbcInboxStore(source, manager, properties);
        }

        @Bean JdbcQuarantineStore workflowQuarantine(DataSource source, DataSourceTransactionManager manager) {
            return new JdbcQuarantineStore(source, manager);
        }

        @Bean(name = "upmsQuarantineStore") JdbcQuarantineStore upmsQuarantine(DataSource source,
                DataSourceTransactionManager manager) {
            return new JdbcQuarantineStore(source, manager);
        }

        @Bean WorkflowEventRecorder workflowEventRecorder(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
                @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
            return new WorkflowEventRecorder(outbox, codec);
        }

        @Bean WorkflowBusinessTaskEventPublisher workflowBusinessTaskEventPublisher(
                @Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
                @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
            return new WorkflowBusinessTaskEventPublisher(outbox, codec);
        }

        @Bean(name = "leaveBusinessTaskRequestDelegate") LeaveBusinessTaskRequestDelegate requestDelegate(
                WorkflowBusinessTaskEventPublisher publisher) {
            return new LeaveBusinessTaskRequestDelegate(publisher);
        }

        @Bean(name = "leaveCompensationRequestDelegate") LeaveCompensationRequestDelegate compensationDelegate(
                WorkflowBusinessTaskEventPublisher publisher) {
            return new LeaveCompensationRequestDelegate(publisher);
        }

        @Bean LeaveBookingService leaveBookingService(LeaveBookingMapper mapper) {
            return new LeaveBookingService(mapper);
        }

        @Bean LeaveWorkflowEventPublisher leaveWorkflowEventPublisher(
                @Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
                @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
            return new LeaveWorkflowEventPublisher(outbox, codec);
        }

        @Bean LeaveWorkflowEventHandler leaveWorkflowEventHandler(LeaveRequestMapper leaves,
                @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
            return new LeaveWorkflowEventHandler(leaves, codec);
        }

        @Bean LeaveBusinessTaskEventHandler leaveBusinessTaskEventHandler(LeaveRequestMapper leaves,
                LeaveBookingService bookings, @Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
                @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
            return new LeaveBusinessTaskEventHandler(leaves, bookings, outbox, codec);
        }

        @Bean WorkflowStartRequestedHandler workflowStartRequestedHandler(
                @Qualifier("workflowEventCodec") WorkflowEventCodec codec,
                ProcessInstanceService processes,
                WorkflowEventRecorder recorder) {
            try {
                return new WorkflowStartRequestedHandler(codec,
                        AopTestUtils.getTargetObject(processes), recorder);
            }
            catch (Exception proxyFailure) {
                throw new IllegalStateException("无法获取 trusted workflow service", proxyFailure);
            }
        }

        @Bean WorkflowBusinessTaskResultHandler workflowBusinessTaskResultHandler(ProcessEngine engine,
                @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
            return new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), engine.getHistoryService(), codec);
        }

        @Bean(name = "workflowInboxExecutor") InboxExecutor workflowInboxExecutor(
                JdbcInboxStore workflowInboxStore, WorkflowStartRequestedHandler start,
                WorkflowBusinessTaskResultHandler result) {
            return new InboxExecutor(workflowInboxStore, "workflow", Map.of(
                    new InboxExecutor.Route("upms", "WORKFLOW_START_REQUESTED", 1), start,
                    new InboxExecutor.Route("upms", "WORKFLOW_BUSINESS_TASK_RESULT", 1), result,
                    new InboxExecutor.Route("upms", "WORKFLOW_COMPENSATION_RESULT", 1), result));
        }

        @Bean(name = "upmsInboxExecutor") InboxExecutor upmsInboxExecutor(
                @Qualifier("upmsInboxStore") JdbcInboxStore upmsInboxStore,
                LeaveWorkflowEventHandler lifecycle, LeaveBusinessTaskEventHandler business) {
            return new InboxExecutor(upmsInboxStore, "upms", Map.of(
                    new InboxExecutor.Route("workflow", "WORKFLOW_STARTED", 1), lifecycle,
                    new InboxExecutor.Route("workflow", "WORKFLOW_START_REJECTED", 1), lifecycle,
                    new InboxExecutor.Route("workflow", "WORKFLOW_COMPLETED", 1), lifecycle,
                    new InboxExecutor.Route("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED", 1), business,
                    new InboxExecutor.Route("workflow", "WORKFLOW_COMPENSATION_REQUESTED", 1), business));
        }
    }
}
