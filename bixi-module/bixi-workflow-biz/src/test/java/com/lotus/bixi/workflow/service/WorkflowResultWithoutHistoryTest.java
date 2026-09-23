package com.lotus.bixi.workflow.service;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The supported history=none setting must not silently disable business writeback. */
@SpringJUnitConfig(WorkflowApprovalIntegrationTest.Config.class)
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {
        "workflow.enabled=true", "workflow.database-schema-update=true", "workflow.history-level=none",
        "workflow.async-executor-activate=false", "flowable.check-process-definitions=false"})
class WorkflowResultWithoutHistoryTest {
    @Autowired ProcessEngine engine;
    @Autowired ProcessInstanceService processes;
    @Autowired WfTaskService tasks;
    @Autowired DataSource dataSource;
    @Autowired WorkflowApprovalIntegrationTest.ResultReceiver receiver;
    @Autowired WorkflowApprovalIntegrationTest.CommandBarrier commandBarrier;

    @AfterEach
    void cleanupTenant() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void businessResultDoesNotDependOnOptionalEngineHistory() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record");
        var deployment = engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/demo_leave_approval.bpmn20.xml").deploy();
        try {
            login(11L);
            var request = new ProcessStartDTO();
        request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setProcessKey("demo_leave_approval");
            request.setBusinessKey("leave-without-history");
            request.setBusinessTable("demo_leave_request");
            request.setBusinessId(123L);
            request.setVariables(Map.of("approverId", "22", "businessRound", 3));
            var started = processes.start(request);
            login(22L);
            var task = engine.getTaskService().createTaskQuery().processInstanceId(started.getProcessInstanceId()).singleResult();
            var approval = new TaskCompleteDTO();
            approval.setRequestId(java.util.UUID.randomUUID().toString());
            approval.setTaskId(task.getId());
            tasks.complete(approval);
            assertThat(engine.getHistoryService().createHistoricVariableInstanceQuery().count()).isZero();
            assertThat(receiver.results).singleElement().satisfies(result -> {
                assertThat(result.getRound()).isEqualTo(3);
                assertThat(result.getStatus()).isEqualTo("completed");
            });
        } finally {
            SecurityContextHolder.clearContext();
            engine.getRepositoryService().deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    void sameRequestReplaysWhenWinnerCommitsAfterInitialMissAndRemovesTask() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record");
        var deployment = engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/demo_leave_approval.bpmn20.xml").deploy();
        var pool = Executors.newSingleThreadExecutor();
        try {
            login(11L);
            var request = new ProcessStartDTO();
            request.setRequestId(UUID.randomUUID().toString());
            request.setProcessKey("demo_leave_approval");
            request.setBusinessKey("replay-after-preflight-miss");
            request.setVariables(Map.of("approverId", "22"));
            var started = processes.start(request);
            var task = engine.getTaskService().createTaskQuery()
                    .processInstanceId(started.getProcessInstanceId()).singleResult();
            var approval = new TaskCompleteDTO();
            approval.setRequestId(UUID.randomUUID().toString());
            approval.setTaskId(task.getId());

            commandBarrier.blockAfterInitialReplayMiss(approval.getRequestId());
            var first = pool.submit(() -> {
                login(22L);
                try {
                    tasks.complete(approval);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertThat(commandBarrier.replayMissObserved.await(10, TimeUnit.SECONDS)).isTrue();

            login(22L);
            tasks.complete(approval);
            commandBarrier.replayMissRelease.countDown();

            assertThatCode(() -> first.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_approval_record", Long.class)).isEqualTo(1L);
            assertThat(engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task.getId()).count()).isZero();
        } finally {
            commandBarrier.clearReplayMiss();
            pool.shutdownNow();
            SecurityContextHolder.clearContext();
            engine.getRepositoryService().deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    void differentTerminalRequestConflictsWithoutEngineHistory() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record");
        var deployment = engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/demo_leave_approval.bpmn20.xml").deploy();
        try {
            login(11L);
            var request = new ProcessStartDTO();
            request.setRequestId(UUID.randomUUID().toString());
            request.setProcessKey("demo_leave_approval");
            request.setBusinessKey("terminal-conflict-without-history");
            request.setVariables(Map.of("approverId", "22"));
            var started = processes.start(request);
            var taskId = engine.getTaskService().createTaskQuery()
                    .processInstanceId(started.getProcessInstanceId()).singleResult().getId();
            login(22L);
            var first = new TaskCompleteDTO();
            first.setTaskId(taskId);
            first.setRequestId(UUID.randomUUID().toString());
            tasks.complete(first);

            var different = new TaskCompleteDTO();
            different.setTaskId(taskId);
            different.setRequestId(UUID.randomUUID().toString());
            assertThatThrownBy(() -> tasks.complete(different))
                    .isInstanceOf(WorkflowOperationConflictException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM wf_command WHERE terminal_task_id = ?", Long.class, taskId)).isEqualTo(1L);

            login(33L);
            assertThatThrownBy(() -> tasks.complete(different)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            SecurityContextHolder.clearContext();
            engine.getRepositoryService().deleteDeployment(deployment.getId(), true);
        }
    }

    private void login(long id) {
        TenantContextHolder.set(1L);
        var permissions = List.of("workflow_process_add", "workflow_process_view", "workflow_task_edit").stream()
                .map(SimpleGrantedAuthority::new).toList();
        var user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null, true, true, true, true, permissions);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, permissions));
    }
}
