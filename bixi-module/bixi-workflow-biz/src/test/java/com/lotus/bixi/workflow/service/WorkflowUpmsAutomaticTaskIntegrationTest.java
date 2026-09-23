package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.event.LeaveBusinessTaskEventHandler;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventHandler;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventPublisher;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.upms.demo.leave.service.LeaveBookingService;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.event.LeaveBusinessTaskRequestDelegate;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskEventPublisher;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskResultHandler;
import com.lotus.bixi.workflow.event.WorkflowEventRecorder;
import com.lotus.bixi.workflow.event.WorkflowStartRequestedHandler;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Cross-owner proof: Flowable completion, UPMS booking, durable result and APPROVED write-back. */
@SpringJUnitConfig({WorkflowApprovalIntegrationTest.Config.class, WorkflowUpmsAutomaticTaskIntegrationTest.Config.class})
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {
        "workflow.enabled=true", "workflow.database-schema-update=true",
        "workflow.async-executor-activate=false", "flowable.check-process-definitions=false"})
class WorkflowUpmsAutomaticTaskIntegrationTest {
    @Autowired ProcessEngine engine;
    @Autowired ProcessInstanceService processes;
    @Autowired WfTaskService tasks;
    @Autowired LeaveWorkflowEventPublisher startPublisher;
    @Autowired LeaveWorkflowEventHandler lifecycleHandler;
    @Autowired LeaveBusinessTaskEventHandler businessHandler;
    @Autowired WorkflowBusinessTaskResultHandler resultHandler;
    @Autowired WorkflowEventRecorder recorder;
    @Autowired @Qualifier("workflowEventCodec") WorkflowEventCodec codec;
    @Autowired @Qualifier("workflowOutboxCapture") OutboxCapture workflowOutbox;
    @Autowired @Qualifier("upmsOutboxCapture") OutboxCapture upmsOutbox;
    @Autowired DataSource source;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record",
                "wf_form_data", "demo_leave_request", "demo_leave_booking");
        workflowOutbox.messages.clear();
        upmsOutbox.messages.clear();
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml").deploy();
        jdbc.update("""
                INSERT INTO demo_leave_request
                    (id, applicant_id, approver_id, start_date, end_date, reason, leave_status,
                     business_key, round, del_flag, status, data_status, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTING', ?, 1, '0', '0', '0', 1)
                """, 101L, 11L, 22L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
                "真实自动任务链路", "demo_leave:101:1");
        WorkflowApprovalIntegrationTest.login(11L);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        engine.getRepositoryService().createDeploymentQuery().list()
                .forEach(deployment -> engine.getRepositoryService().deleteDeployment(deployment.getId(), true));
    }

    @Test
    void humanApprovalBooksLeaveAndWritesApprovedStateThroughBothOwners() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(101L);
        leave.setApplicantId(11L);
        leave.setApproverId(22L);
        leave.setStartDate(LocalDate.of(2026, 10, 1));
        leave.setEndDate(LocalDate.of(2026, 10, 2));
        leave.setReason("真实自动任务链路");
        leave.setRound(1);
        leave.setBusinessKey("demo_leave:101:1");
        LeaveWorkflowEventPublisher.Published published = startPublisher.publishStart(leave, actor(11L));
        jdbc.update("UPDATE demo_leave_request SET start_command_id = ?, start_request_hash = ? WHERE id = 101",
                published.commandId(), published.requestHash());

        WorkflowEvent start = codec.decode(message(upmsOutbox, "WORKFLOW_START_REQUESTED").payloadJson()
                .getBytes(StandardCharsets.UTF_8));
        assertThat(workflowStartHandler().handle(message(upmsOutbox, "WORKFLOW_START_REQUESTED")))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        DurableMessage startedMessage = message(workflowOutbox, "WORKFLOW_STARTED");
        assertThat(lifecycleHandler.handle(startedMessage))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        String processId = start.processInstanceId() == null
                ? codec.decode(startedMessage.payloadJson().getBytes(StandardCharsets.UTF_8)).processInstanceId()
                : start.processInstanceId();

        String taskId = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult().getId();
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult().getTenantId())
                .isEqualTo("1");
        WorkflowApprovalIntegrationTest.login(22L);
        TaskCompleteDTO complete = new TaskCompleteDTO();
        complete.setRequestId(UUID.randomUUID().toString());
        complete.setTaskId(taskId);
        complete.setApprovalComment("同意");
        tasks.complete(complete);

        DurableMessage requestMessage = message(workflowOutbox, "WORKFLOW_BUSINESS_TASK_REQUESTED");
        assertThat(businessHandler.handle(requestMessage))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        DurableMessage resultMessage = message(upmsOutbox, "WORKFLOW_BUSINESS_TASK_RESULT");
        assertThat(resultHandler.handle(resultMessage))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);
        DurableMessage terminalMessage = message(workflowOutbox, "WORKFLOW_COMPLETED");
        assertThat(lifecycleHandler.handle(terminalMessage))
                .isEqualTo(com.lotus.bixi.common.mq.reliable.DurableMessageHandler.Result.PROCESSED);

        assertThat(jdbc.queryForObject("SELECT booking_state FROM demo_leave_booking WHERE leave_id = 101",
                String.class)).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("SELECT leave_status FROM demo_leave_request WHERE id = 101",
                String.class)).isEqualTo("APPROVED");
        assertThat(processes.getById(processId).getStatus()).isEqualTo("completed");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).count())
                .isZero();
    }

    private WorkflowStartRequestedHandler workflowStartHandler() {
        try {
            return new WorkflowStartRequestedHandler(codec, AopTestUtils.getTargetObject(processes), recorder);
        }
        catch (Exception proxyFailure) {
            throw new IllegalStateException("无法获取 trusted workflow service", proxyFailure);
        }
    }

    private BixiUser actor(long id) {
        return new BixiUser(id, 1L, 1L, "user-" + id, "unused", null,
                true, true, true, true, List.of());
    }

    private DurableMessage message(OutboxCapture capture, String type) {
        return capture.messages.stream().filter(message -> type.equals(message.type())).findFirst().orElseThrow();
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan("com.lotus.bixi.upms.demo.leave.mapper")
    @Import(MybatisAutoConfiguration.class)
    @ImportAutoConfiguration({MybatisPlusAutoConfiguration.class})
    static class Config {
        @Bean("workflowOutboxCapture") OutboxCapture workflowOutboxCapture() { return new OutboxCapture(); }

        @Bean("upmsOutboxCapture") OutboxCapture upmsOutboxCapture() { return new OutboxCapture(); }

        @Bean("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore(
                @Qualifier("workflowOutboxCapture") OutboxCapture capture) {
            return store(capture);
        }

        @Bean("upmsOutboxStore") JdbcOutboxStore upmsOutboxStore(
                @Qualifier("upmsOutboxCapture") OutboxCapture capture) {
            return store(capture);
        }

        @Bean("workflowEventCodec") WorkflowEventCodec workflowEventCodec() { return new WorkflowEventCodec(); }

        @Bean("leaveWorkflowEventCodec") WorkflowEventCodec leaveWorkflowEventCodec() {
            return new WorkflowEventCodec();
        }

        @Bean WorkflowEventRecorder workflowEventRecorder(
                @Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
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

        @Bean WorkflowBusinessTaskResultHandler workflowBusinessTaskResultHandler(
                ProcessEngine engine, @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
            return new WorkflowBusinessTaskResultHandler(engine.getRuntimeService(), codec);
        }

        private static JdbcOutboxStore store(OutboxCapture capture) {
            JdbcOutboxStore store = mock(JdbcOutboxStore.class);
            doAnswer(invocation -> {
                capture.messages.add(invocation.getArgument(0, DurableMessage.class));
                return null;
            }).when(store).enqueue(any(), any(), any(), any());
            return store;
        }
    }

    static final class OutboxCapture {
        final List<DurableMessage> messages = new ArrayList<>();
    }
}
