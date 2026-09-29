package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.common.workflow.config.WorkflowAutoConfiguration;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.dto.TaskResolveDTO;
import com.lotus.bixi.workflow.api.dto.TaskCommentDTO;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException;
import com.lotus.bixi.workflow.api.service.WorkflowResultReceiver;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.controller.TaskController;
import com.lotus.bixi.workflow.controller.ProcessDefinitionController;
import com.lotus.bixi.workflow.controller.FormDataController;
import com.lotus.bixi.workflow.config.WorkflowEngineEventsConfiguration;
import com.lotus.bixi.workflow.event.WorkflowTaskNotificationSink;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Actual business services, canonical schema, MyBatis and Flowable; no mocked implementation. */
@SpringJUnitConfig(WorkflowApprovalIntegrationTest.Config.class)
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {"workflow.enabled=true",
        "workflow.database-schema-update=true", "workflow.async-executor-activate=false",
        "workflow.public-start-models=approval,plain,candidate,immediate,terminate,parallel,leave_approval,expense_approval",
        "flowable.check-process-definitions=false"})
class WorkflowApprovalIntegrationTest {
    private static final String FORM_SCHEMA = """
            {"widgetList":[
              {"type":"number","options":{"name":"amount","required":true,"min":1}},
              {"type":"input","options":{"name":"note","maxLength":20}},
              {"type":"input","options":{"name":"secret"}}
            ]}
            """;

    @Autowired ProcessEngine engine;
    @Autowired ProcessInstanceService processes;
    @Autowired TrustedProcessStarter trustedProcesses;
    @Autowired WfTaskService tasks;
    @Autowired ProcessDefinitionService definitions;
    @Autowired DataSource dataSource;
    @Autowired ResultReceiver resultReceiver;
    @Autowired ProcessDefinitionController definitionController;
    @Autowired FormDataService formData;
    @Autowired CommandBarrier commandBarrier;
    @Autowired WorkflowTaskNotificationSink taskNotifications;
    JdbcTemplate jdbc;

    @BeforeEach
    void schemaAndModel() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        resultReceiver.results.clear();
        resultReceiver.fail = false;
        commandBarrier.commandBarrier = null;
        commandBarrier.processBarrier = null;
        commandBarrier.clearReplayMiss();
        reset(taskNotifications);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record", "wf_form",
                "wf_form_version", "wf_process_definition", "wf_form_data", "sys_form_permission",
                "sys_role_form_permission");
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addString("approval.bpmn20.xml", model("approval", true))
                .addString("plain.bpmn20.xml", model("plain", true).replaceAll("(?s)<extensionElements>.*?</extensionElements>", ""))
                .addString("candidate.bpmn20.xml", model("candidate", true)
                        .replace("flowable:assignee=\"${approverId}\"", "flowable:candidateUsers=\"22,33\""))
                .addString("immediate.bpmn20.xml", model("immediate", false)).deploy();
        login(11L);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        engine.getRepositoryService().createDeploymentQuery().list()
                .forEach(d -> engine.getRepositoryService().deleteDeployment(d.getId(), true));
    }

    @Test
    void lostStartResponseReturnsTheSavedResult() {
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setRequestId(java.util.UUID.randomUUID().toString());
        dto.setProcessKey("approval");
        dto.setBusinessKey("lost-start");
        dto.setTitle("same intention");
        dto.setVariables(Map.of("approverId", "22"));
        var first = processes.start(dto);
        var second = processes.start(dto);
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceBusinessKey("lost-start").count()).isEqualTo(1);
    }

    @Test
    void taskCreationWithoutAnEmbeddedBpmnListenerStillRecordsItsNotification() {
        ProcessInstanceVO started = start("plain", "global-task-notification");

        org.mockito.ArgumentCaptor<WorkflowTaskNotificationSink.Context> captured =
                org.mockito.ArgumentCaptor.forClass(WorkflowTaskNotificationSink.Context.class);
        verify(taskNotifications).record(captured.capture());
        assertThat(captured.getValue().processInstanceId()).isEqualTo(started.getProcessInstanceId());
        assertThat(captured.getValue().recipientUserIds()).containsExactly(22L);
    }

    @Test
    void candidateTaskCreationRecordsTheFlowableCandidatePool() {
        ProcessInstanceVO started = start("candidate", "candidate-task-notification");

        org.mockito.ArgumentCaptor<WorkflowTaskNotificationSink.Context> captured =
                org.mockito.ArgumentCaptor.forClass(WorkflowTaskNotificationSink.Context.class);
        verify(taskNotifications).record(captured.capture());
        assertThat(captured.getValue().processInstanceId()).isEqualTo(started.getProcessInstanceId());
        assertThat(captured.getValue().recipientUserIds()).containsExactly(22L, 33L);
        assertThat(captured.getValue().recipientRoleIds()).isEmpty();
    }

    @Test
    void taskCompletionUsesTheFrozenFormAndMergesOnlyEditableFields() {
        bindApprovalForm();
        ProcessInstanceVO started = startWithForm("form-merge");
        String taskId = taskId(started);
        loginWithRole(22L, 9L);

        TaskCompleteDTO complete = approve(taskId, "通过");
        complete.setFormId(7L);
        complete.setFormDataJson("{\"note\":\"updated\"}");
        tasks.complete(complete);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form_data WHERE process_instance_id = ?",
                Long.class, started.getProcessInstanceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT data_json FROM wf_form_data WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId()))
                .isEqualTo("{\"amount\":10,\"note\":\"updated\",\"secret\":\"classified\"}");
        assertThat(jdbc.queryForObject("SELECT form_version_id FROM wf_form_data WHERE process_instance_id = ?",
                Long.class, started.getProcessInstanceId())).isEqualTo(71L);
        assertThat(jdbc.queryForObject("SELECT task_id FROM wf_form_data WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId())).isEqualTo(taskId);
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
    }

    @Test
    void taskCompletionRejectsClientBindingAndProtectedFieldWritesBeforeMutation() {
        bindApprovalForm();
        ProcessInstanceVO forgedInstance = startWithForm("forged-form");
        ProcessInstanceVO readonlyInstance = startWithForm("readonly-field");
        ProcessInstanceVO hiddenInstance = startWithForm("hidden-field");
        ProcessInstanceVO invalidInstance = startWithForm("invalid-field");
        loginWithRole(22L, 9L);

        TaskCompleteDTO forged = completionWithForm(taskId(forgedInstance), 8L, "{\"note\":\"changed\"}");
        TaskCompleteDTO readonly = completionWithForm(taskId(readonlyInstance), 7L, "{\"amount\":20}");
        TaskCompleteDTO hidden = completionWithForm(taskId(hiddenInstance), 7L, "{\"secret\":\"changed\"}");
        TaskCompleteDTO invalid = completionWithForm(taskId(invalidInstance), 7L,
                "{\"note\":\"this value is longer than twenty characters\"}");

        for (TaskCompleteDTO rejected : List.of(forged, readonly, hidden, invalid)) {
            assertThatThrownBy(() -> tasks.complete(rejected)).isInstanceOf(IllegalArgumentException.class);
            assertThat(engine.getTaskService().createTaskQuery().taskId(rejected.getTaskId()).count()).isEqualTo(1L);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_approval_record", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form_data", Long.class)).isEqualTo(4L);
    }

    @Test
    void formSnapshotFailureRollsBackTaskApprovalAndCommandTogether() {
        bindApprovalForm();
        ProcessInstanceVO started = startWithForm("form-rollback");
        String taskId = taskId(started);
        loginWithRole(22L, 9L);
        TaskCompleteDTO complete = completionWithForm(taskId, 7L, "{\"note\":\"updated\"}");
        jdbc.execute("ALTER TABLE wf_form_data ADD CONSTRAINT reject_form_update "
                + "CHECK (data_json NOT LIKE '%updated%')");

        assertThatThrownBy(() -> tasks.complete(complete)).isInstanceOf(RuntimeException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).count()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_approval_record", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT data_json FROM wf_form_data WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId()))
                .isEqualTo("{\"amount\":10,\"note\":\"old\",\"secret\":\"classified\"}");

        dropCheck("wf_form_data", "reject_form_update");
        assertThatCode(() -> tasks.complete(complete)).doesNotThrowAnyException();
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).count()).isZero();
    }

    @Test
    void formDataReadsArePermissionFilteredAndDirectWritesAreRejected() {
        bindApprovalForm();
        ProcessInstanceVO started = startWithForm("filtered-render");
        String taskId = taskId(started);
        loginWithRole(22L, 9L);

        var byTask = formData.renderByTaskId(taskId);
        var byProcess = formData.renderByProcessInstanceId(started.getProcessInstanceId());
        assertThat(byTask.getSchemaJson()).contains("amount", "note").doesNotContain("secret");
        assertThat(byTask.getDataJson()).contains("amount", "note").doesNotContain("secret", "classified");
        assertThat(byProcess).usingRecursiveComparison().isEqualTo(byTask);

        FormDataDTO direct = new FormDataDTO();
        direct.setFormId(7L);
        direct.setProcessInstanceId(started.getProcessInstanceId());
        direct.setDataJson("{\"secret\":\"exposed\"}");
        assertThatThrownBy(() -> new FormDataController(formData).save(direct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("流程发起或任务办理");
    }

    @Test
    void bundledDefinitionCanBeDeployedRepeatedlyThroughTheSharedEndpoint() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(definitionController).build();
        mvc.perform(post("/workflow/definition/deploy-demo")).andExpect(status().isOk());
        mvc.perform(post("/workflow/definition/deploy-demo")).andExpect(status().isOk());
        assertThat(engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("demo_leave_approval").count()).isEqualTo(1);
    }

    @Test
    void definitionListUsesTheSamePermissionAsItsFrontend() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(definitionController).build();
        mvc.perform(get("/workflow/definition/list")).andExpect(status().isOk());
    }

    @Test
    void startableCatalogIsLatestActiveCategoryScopedAndDoesNotExposeTheAdminList() {
        var repository = engine.getRepositoryService();
        var firstDeployment = repository.createDeployment().tenantId("1")
                .addString("catalog-v1.bpmn20.xml", model("catalog", true)).deploy();
        var first = repository.createProcessDefinitionQuery().deploymentId(firstDeployment.getId()).singleResult();
        repository.setProcessDefinitionCategory(first.getId(), "finance");
        var secondDeployment = repository.createDeployment().tenantId("1")
                .addString("catalog-v2.bpmn20.xml", model("catalog", true)).deploy();
        var second = repository.createProcessDefinitionQuery().deploymentId(secondDeployment.getId()).singleResult();
        repository.setProcessDefinitionCategory(second.getId(), "finance");
        var officeDeployment = repository.createDeployment().tenantId("1")
                .addString("office.bpmn20.xml", model("office", true)).deploy();
        var office = repository.createProcessDefinitionQuery().deploymentId(officeDeployment.getId()).singleResult();
        repository.setProcessDefinitionCategory(office.getId(), "office");

        ProcessQueryDTO finance = new ProcessQueryDTO();
        finance.setCategory(" finance ");
        login(77L, List.of("workflow_process_add"));

        repository.suspendProcessDefinitionById(second.getId());
        assertThat(definitions.listStartableDefinitions(finance)).isEmpty();
        repository.activateProcessDefinitionById(second.getId());
        assertThat(definitions.listStartableDefinitions(finance))
                .extracting(definition -> definition.getProcessDefinitionId())
                .containsExactly(second.getId());
        assertThatThrownBy(() -> definitions.listDefinitions(new ProcessQueryDTO()))
                .isInstanceOf(AccessDeniedException.class);

        login(77L, List.of("workflow_definition_view"));
        assertThat(definitions.listDefinitions(finance))
                .extracting(definition -> definition.getProcessDefinitionId())
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void todoExposesPendingDelegationForItsActualHandler() {
        var started = start("approval", "delegation-view");
        login(22L);
        var dto = new TaskTransferDTO();
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setTaskId(taskId(started));
        dto.setTransferUserId(33L);
        tasks.delegate(dto);
        login(33L);
        assertThat(tasks.getById(dto.getTaskId()).getDelegationState()).isEqualTo("PENDING");
    }

    @Test
    void doneExposesTheActualEngineCompletionTime() {
        var started = start("approval", "done-time");
        login(22L);
        var taskId = taskId(started);
        tasks.complete(approve(taskId, "通过"));
        assertThat(tasks.getById(taskId).getEndTime()).isNotNull();
    }

    @Test
    void approvalNotifiesBusinessWithCommittedIdentityAndRound() {
        var started = startBusiness("approval");
        assertThat(resultReceiver.results).isEmpty();
        login(22L);
        tasks.complete(approve(taskId(started), "完成"));
        assertThat(resultReceiver.results).singleElement().satisfies(result -> {
            assertThat(result.getProcessInstanceId()).isEqualTo(started.getProcessInstanceId());
            assertThat(result.getBusinessKey()).isEqualTo(started.getBusinessKey());
            assertThat(result.getBusinessTable()).isEqualTo("demo_leave_request");
            assertThat(result.getBusinessId()).isEqualTo(123L);
            assertThat(result.getStartUserId()).isEqualTo(11L);
            assertThat(result.getRound()).isEqualTo(2);
            assertThat(result.getStatus()).isEqualTo("completed");
            assertThat(result.getEndTime()).isNotNull();
            assertThat(result.getEventId()).isNotBlank();
        });
    }

    @Test
    void rejectionAndCancellationNotifyDistinctOutcomes() {
        var rejected = startBusiness("approval");
        login(22L);
        var reject = new TaskRejectDTO();
        reject.setRequestId(UUID.randomUUID().toString());
        reject.setTaskId(taskId(rejected));
        reject.setRejectReason("不符合条件");
        tasks.reject(reject);
        login(11L);
        var canceled = startBusiness("approval", 3);
        processes.terminate(canceled.getProcessInstanceId(), "申请人取消", UUID.randomUUID().toString());
        assertThat(resultReceiver.results).extracting(WorkflowResultDTO::getStatus)
                .containsExactly("rejected", "terminated");
    }

    @Test
    void synchronousTrustedStartReturnsTheCommittedTerminalExtension() {
        var started = startBusiness("immediate");
        assertThat(started.getStatus()).isEqualTo("completed");
        assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE process_instance_id = ?",
                String.class, started.getProcessInstanceId())).isEqualTo("completed");
    }

    @Test
    void failedApprovalTransactionDoesNotSendBusinessResult() {
        var started = startBusiness("approval");
        jdbc.execute("ALTER TABLE wf_approval_record ADD CONSTRAINT reject_result_audit CHECK (approval_user_id <> 22)");
        login(22L);
        assertThatThrownBy(() -> tasks.complete(approve(taskId(started), "将回滚")))
                .isInstanceOf(RuntimeException.class);
        assertThat(resultReceiver.results).isEmpty();
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("running");
    }

    @Test
    void unavailableReceiverDoesNotUndoCommittedApproval() {
        var started = startBusiness("approval");
        resultReceiver.fail = true;
        login(22L);
        tasks.complete(approve(taskId(started), "已完成"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
        assertThat(resultReceiver.results).isEmpty();
    }

    @Test
    void startPersistsRunningStateAndTrustedInitiator() {
        ProcessInstanceVO started = start("approval", "normal");
        assertThat(started.getStatus()).isEqualTo("running");
        assertThat(started.getStartUserId()).isEqualTo(11L);
        assertThat(engine.getRuntimeService().getVariable(started.getProcessInstanceId(), "applicant")).isEqualTo("11");
    }

    @Test
    void approvalCompletesEngineExtensionAndAuditTogether() {
        var started = start("approval", "approve");
        String task = taskId(started);
        login(22L);
        tasks.complete(approve(task, "同意"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).singleElement()
                .satisfies(record -> {
                    assertThat(record.getApprovalUserId()).isEqualTo(22L);
                    assertThat(record.getApprovalType()).isEqualTo("approve");
                });
        assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(started.getProcessInstanceId()).finished().count()).isEqualTo(1);
        assertThatThrownBy(() -> tasks.complete(approve(task, "重复办理")))
                .isInstanceOf(WorkflowOperationConflictException.class);
    }

    @Test
    void repeatedCompleteWithSameRequestIdReturnsStableSuccessAndOneAudit() {
        var started = start("approval", "idempotent-complete");
        String task = taskId(started);
        login(22L);
        var complete = approveWithRequestId(task, "同一意图", UUID.randomUUID().toString());

        tasks.complete(complete);
        tasks.complete(complete);

        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).singleElement()
                .extracting("approvalType").isEqualTo("approve");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void missingAndEmptyCompletionVariablesReplayTheSameRequest() {
        var started = start("approval", "empty-completion-variables");
        String task = taskId(started);
        login(22L);
        var first = approveWithRequestId(task, "同一意图", UUID.randomUUID().toString());
        tasks.complete(first);

        var retry = approveWithRequestId(task, "同一意图", first.getRequestId());
        retry.setVariables(Map.of());
        assertThatCode(() -> tasks.complete(retry)).doesNotThrowAnyException();
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
    }

    @Test
    void changedCompletePayloadWithSameRequestIdIsAConflict() {
        var started = start("approval", "idempotent-conflict");
        String task = taskId(started);
        login(22L);
        String requestId = UUID.randomUUID().toString();
        tasks.complete(approveWithRequestId(task, "原始意见", requestId));

        var changed = approveWithRequestId(task, "修改意见", requestId);
        assertThatThrownBy(() -> tasks.complete(changed)).isInstanceOf(WorkflowRequestConflictException.class);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
    }

    @Test
    void changingTerminalOperationWithSameRequestIdIsARequestConflict() {
        var started = start("approval", "cross-operation-conflict");
        String task = taskId(started);
        login(22L);
        String requestId = UUID.randomUUID().toString();
        tasks.complete(approveWithRequestId(task, "原始意见", requestId));

        var changedOperation = new TaskRejectDTO();
        changedOperation.setTaskId(task);
        changedOperation.setRejectReason("改为拒绝");
        changedOperation.setRequestId(requestId);
        assertThatThrownBy(() -> tasks.reject(changedOperation))
                .isInstanceOf(WorkflowRequestConflictException.class);
    }

    @Test
    void differentTerminalRequestsHaveOneWinnerAndNoPartialLoserAudit() throws Exception {
        var started = start("approval", "idempotent-race");
        String task = taskId(started);
        commandBarrier.commandBarrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var complete = pool.submit(() -> {
            login(22L);
            try {
                tasks.complete(approveWithRequestId(task, "通过", UUID.randomUUID().toString()));
                return (Object) "complete";
            } catch (RuntimeException error) {
                return error;
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        var reject = pool.submit(() -> {
            login(22L);
            var dto = new TaskRejectDTO();
            dto.setTaskId(task);
            dto.setRejectReason("拒绝");
            dto.setRequestId(UUID.randomUUID().toString());
            try {
                tasks.reject(dto);
                return (Object) "reject";
            } catch (RuntimeException error) {
                return error;
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        Object first;
        Object second;
        try {
            first = complete.get(20, TimeUnit.SECONDS);
            second = reject.get(20, TimeUnit.SECONDS);
        } finally {
            commandBarrier.commandBarrier = null;
            pool.shutdownNow();
        }

        assertThat(List.of(first, second).stream().filter(String.class::isInstance)).hasSize(1);
        assertThat(List.of(first, second).stream().filter(Throwable.class::isInstance))
                .singleElement().isInstanceOf(WorkflowOperationConflictException.class);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE status = 'SUCCEEDED' AND operation <> 'START'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void transferAndCompleteRecheckTaskAfterWaitingForTheProcessLock() throws Exception {
        var started = start("approval", "transfer-complete-race");
        String task = taskId(started);
        commandBarrier.processBarrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var complete = pool.submit(() -> runAs(22L, () -> tasks.complete(
                approveWithRequestId(task, "通过", UUID.randomUUID().toString())), "complete"));
        var transfer = pool.submit(() -> runAs(22L, () -> {
            var dto = new TaskTransferDTO();
            dto.setTaskId(task);
            dto.setTransferUserId(33L);
            dto.setRequestId(UUID.randomUUID().toString());
            tasks.transfer(dto);
        }, "transfer"));
        Object first;
        Object second;
        try {
            first = complete.get(20, TimeUnit.SECONDS);
            second = transfer.get(20, TimeUnit.SECONDS);
        } finally {
            commandBarrier.processBarrier = null;
            pool.shutdownNow();
        }

        assertThat(List.of(first, second).stream().filter(String.class::isInstance)).hasSize(1);
        assertThat(List.of(first, second).stream().filter(Throwable.class::isInstance)).singleElement()
                .matches(error -> error instanceof IllegalArgumentException || error instanceof AccessDeniedException);
        login(11L);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE status = 'SUCCEEDED' AND operation <> 'START'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void terminateAndCompleteRecheckStateAfterWaitingForTheProcessLock() throws Exception {
        var started = start("approval", "terminate-complete-race");
        String task = taskId(started);
        commandBarrier.processBarrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var complete = pool.submit(() -> runAs(22L, () -> tasks.complete(
                approveWithRequestId(task, "通过", UUID.randomUUID().toString())), "complete"));
        var terminate = pool.submit(() -> runAs(11L, () -> processes.terminate(started.getProcessInstanceId(),
                "取消", UUID.randomUUID().toString()), "terminate"));
        Object first;
        Object second;
        try {
            first = complete.get(20, TimeUnit.SECONDS);
            second = terminate.get(20, TimeUnit.SECONDS);
        } finally {
            commandBarrier.processBarrier = null;
            pool.shutdownNow();
        }

        assertThat(List.of(first, second).stream().filter(String.class::isInstance)).hasSize(1);
        assertThat(List.of(first, second).stream().filter(Throwable.class::isInstance)).singleElement()
                .isInstanceOf(IllegalArgumentException.class);
        login(11L);
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isIn("completed", "terminated");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE status = 'SUCCEEDED' AND operation <> 'START'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void engineCompletionUpdatesExtensionWithoutModelListener() {
        var started = start("plain", "plain-model");
        login(22L);
        tasks.complete(approve(taskId(started), "通过"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
    }

    @Test
    void terminateEndFinishesExtensionAfterHumanTask() {
        engine.getRepositoryService().createDeployment().addString("terminate.bpmn20.xml",
                model("terminate", true).replace("</endEvent>", "<terminateEventDefinition/></endEvent>")).deploy();
        var started = start("terminate", "terminate-end");
        login(22L);
        tasks.complete(approve(taskId(started), "完成"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("terminated");
    }

    @Test
    void directCandidateCanReadBeforeClaimButLosesEligibilityWhenAnotherUserClaims() {
        var started = start("candidate", "candidate");
        String task = taskId(started);
        login(33L);
        assertThat(tasks.todoPage(new Page<>(1, 10), 33L).getRecords()).extracting("taskId").contains(task);
        assertThat(tasks.getById(task).getTaskId()).isEqualTo(task);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).isEmpty();
        login(22L);
        assertThatThrownBy(() -> tasks.claim(task, 22L, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(task).singleResult().getAssignee()).isNull();
        tasks.claim(task, 22L, UUID.randomUUID().toString());
        assertThatThrownBy(() -> tasks.unclaim(task, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(task).singleResult().getAssignee()).isEqualTo("22");
        login(33L);
        assertThatThrownBy(() -> tasks.getById(task)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void individualParallelEndDoesNotCompleteWholeProcess() {
        engine.getRepositoryService().createDeployment().addString("parallel.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.example/test">
                  <process id="parallel" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s" sourceRef="start" targetRef="split"/>
                    <parallelGateway id="split"/>
                    <sequenceFlow id="a" sourceRef="split" targetRef="first"/>
                    <sequenceFlow id="b" sourceRef="split" targetRef="second"/>
                    <userTask id="first" flowable:assignee="22"/>
                    <userTask id="second" flowable:assignee="33"/>
                    <sequenceFlow id="c" sourceRef="first" targetRef="end1"/>
                    <sequenceFlow id="d" sourceRef="second" targetRef="end2"/>
                    <endEvent id="end1"><extensionElements>
                      <flowable:executionListener event="end" delegateExpression="${processEndListener}"/>
                    </extensionElements></endEvent>
                    <endEvent id="end2"><extensionElements>
                      <flowable:executionListener event="end" delegateExpression="${processEndListener}"/>
                    </extensionElements></endEvent>
                  </process>
                </definitions>
                """).deploy();
        var started = start("parallel", "parallel-model");
        login(22L);
        String first = engine.getTaskService().createTaskQuery().processInstanceId(started.getProcessInstanceId())
                .taskAssignee("22").singleResult().getId();
        tasks.complete(approve(first, "第一分支通过"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("running");
        login(33L);
        tasks.complete(approve(taskId(started), "第二分支通过"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
    }

    @Test
    void rejectionIsTerminalAndDifferentFromCancellation() {
        var started = start("approval", "reject");
        login(22L);
        TaskRejectDTO reject = new TaskRejectDTO();
        reject.setRequestId(UUID.randomUUID().toString());
        reject.setTaskId(taskId(started));
        reject.setRejectReason("不符合条件");
        tasks.reject(reject);
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("rejected");
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).singleElement()
                .satisfies(record -> assertThat(record.getApprovalType()).isEqualTo("reject"));
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(started.getProcessInstanceId()).count()).isZero();
    }

    @Test
    void nonParticipantCannotReadOrCompleteEvenWithOperationPermissions() {
        var started = start("approval", "private");
        String task = taskId(started);
        login(33L);
        assertThatThrownBy(() -> tasks.complete(approve(task, "越权"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tasks.getById(task)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> processes.getById(started.getProcessInstanceId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> processes.getApprovalHistory(started.getProcessInstanceId())).isInstanceOf(AccessDeniedException.class);
        assertThat(processes.page(new Page<>(1, 10), new ProcessQueryDTO()).getRecords()).isEmpty();
        assertThatThrownBy(() -> tasks.todoPage(new Page<>(1, 10), 22L)).isInstanceOf(AccessDeniedException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(task).count()).isEqualTo(1);
    }

    @Test
    void processPermissionsAreEnforcedForDirectServiceCalls() {
        login(11L, List.of());
        assertThatThrownBy(() -> start("approval", "no-permission")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tasks.todoPage(new Page<>(1, 10), 11L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectionDoesNotAllowArbitraryActivityJumps() {
        var started = start("approval", "no-jump");
        login(22L);
        TaskRejectDTO reject = new TaskRejectDTO();
        reject.setRequestId(UUID.randomUUID().toString());
        reject.setTaskId(taskId(started));
        reject.setRejectReason("退回");
        reject.setTargetActivityId("start");
        assertThatThrownBy(() -> tasks.reject(reject)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.getTaskService().createTaskQuery().processInstanceId(started.getProcessInstanceId()).count()).isEqualTo(1);
    }

    @Test
    void synchronousEndPersistsCompletedStateAfterListenerRan() {
        var started = start("immediate", "immediate");
        assertThat(started.getStatus()).isEqualTo("completed");
        assertThat(started.getEndTime()).isNotNull();
    }

    @Test
    void auditFailureRollsBackTaskCompletionAndExtensionUpdate() {
        var started = start("approval", "rollback");
        String task = taskId(started);
        var request = approve(task, "ROLLBACK");
        jdbc.execute("ALTER TABLE wf_approval_record ADD CONSTRAINT reject_test_comment CHECK (approval_comment <> 'ROLLBACK')");
        login(22L);
        assertThatThrownBy(() -> tasks.complete(request)).isInstanceOf(RuntimeException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(task).count()).isEqualTo(1);
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("running");
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class)).isZero();
        dropCheck("wf_approval_record", "reject_test_comment");
        tasks.complete(request);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class)).isEqualTo(1L);
    }

    @Test
    void rejectTreatsBlankAndMissingTargetAsTheSameRequest() {
        var started = start("approval", "reject-normalization");
        String task = taskId(started);
        String requestId = UUID.randomUUID().toString();
        var first = new TaskRejectDTO();
        first.setTaskId(task);
        first.setRejectReason("拒绝");
        first.setTargetActivityId(" ");
        first.setRequestId(requestId);
        login(22L);
        tasks.reject(first);

        var replay = new TaskRejectDTO();
        replay.setTaskId(task);
        replay.setRejectReason("拒绝");
        replay.setRequestId(requestId);
        assertThatCode(() -> tasks.reject(replay)).doesNotThrowAnyException();
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
    }

    @Test
    void instanceWriteFailureRollsBackEngineStart() {
        jdbc.execute("ALTER TABLE wf_process_instance ADD CONSTRAINT reject_test_title CHECK (title <> 'ROLLBACK')");
        assertThatThrownBy(() -> start("approval", "ROLLBACK")).isInstanceOf(RuntimeException.class);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey("approval").count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_process_instance", Integer.class)).isZero();
    }

    @Test
    void approvalCannotOverwriteTrustedProcessIdentity() {
        var started = start("approval", "variables");
        login(22L);
        var complete = approve(taskId(started), "身份不能改写");
        complete.setVariables(Map.of("applicant", "33"));
        assertThatThrownBy(() -> tasks.complete(complete)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.getTaskService().createTaskQuery().taskId(complete.getTaskId()).count()).isEqualTo(1);
    }

    @Test
    void bundledModelsUseExecutableListeners() {
        for (String key : List.of("leave_approval", "expense_approval")) {
            engine.getRepositoryService().createDeployment()
                    .addClasspathResource("processes/" + key + ".bpmn20.xml").deploy();
            var started = start(key, "bundled");
            assertThat(tasks.getById(taskId(started)).getAssignee()).isEqualTo("11");
        }
    }

    @Test
    void leaveDemoCanBeDeployedRepeatedlyAndApproved() {
        for (int i = 0; i < 2; i++) {
            engine.getRepositoryService().createDeployment().tenantId("1").name("bixi-leave-demo")
                    .enableDuplicateFiltering()
                    .addClasspathResource("processes/demo_leave_approval.bpmn20.xml").deploy();
        }
        assertThat(engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("demo_leave_approval").count()).isEqualTo(1);
        var started = start("demo_leave_approval", "请假示例");
        login(22L);
        tasks.complete(approve(taskId(started), "同意"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
    }

    @Test
    void definitionsAreQueriedAndSuspendedThroughActualService() {
        assertThat(definitions.listLatestVersions()).extracting("processKey").contains("approval", "immediate");
        var definition = definitions.getByKey("approval");
        assertThat(definition.getProcessKey()).isEqualTo("approval");
        definitions.suspend(definition.getProcessDefinitionId());
        assertThatThrownBy(() -> start("approval", "suspended-definition")).isInstanceOf(RuntimeException.class);
        definitions.activate(definition.getProcessDefinitionId());
        assertThat(start("approval", "resumed-definition").getStatus()).isEqualTo("running");
    }

    @Test
    void transferMovesAuthorityAndKeepsActorHistory() {
        var started = start("approval", "transfer");
        String task = taskId(started);
        TaskTransferDTO transfer = new TaskTransferDTO();
        transfer.setRequestId(UUID.randomUUID().toString());
        transfer.setTaskId(task);
        transfer.setTransferUserId(33L);
        transfer.setTransferReason("交接");
        login(33L);
        assertThatThrownBy(() -> tasks.transfer(transfer)).isInstanceOf(AccessDeniedException.class);
        login(22L);
        tasks.transfer(transfer);
        assertThatThrownBy(() -> tasks.complete(approve(task, "旧办理人"))).isInstanceOf(AccessDeniedException.class);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(1);
        login(33L);
        assertThat(tasks.todoPage(new Page<>(1, 10), 33L).getRecords()).extracting("taskId").contains(task);
        tasks.complete(approve(task, "新办理人通过"));
        assertThat(tasks.donePage(new Page<>(1, 10), 33L).getRecords()).extracting("taskId").contains(task);
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).hasSize(2);
    }

    @Test
    void delegateResolvesBackToOwnerBeforeApproval() throws Exception {
        var started = start("approval", "delegate");
        String task = taskId(started);
        TaskTransferDTO delegate = new TaskTransferDTO();
        delegate.setRequestId(UUID.randomUUID().toString());
        delegate.setTaskId(task);
        delegate.setTransferUserId(33L);
        delegate.setTransferReason("请协助核实");
        login(22L);
        tasks.delegate(delegate);
        var resolve = new TaskResolveDTO();
        resolve.setRequestId(UUID.randomUUID().toString());
        resolve.setTaskId(task);
        resolve.setComment("协助完成");
        login(11L);
        assertThatThrownBy(() -> tasks.resolve(resolve)).isInstanceOf(AccessDeniedException.class);
        login(33L);
        assertThatThrownBy(() -> tasks.complete(approve(task, "不能替代原审批人")))
                .isInstanceOf(IllegalArgumentException.class);
        var mvc = MockMvcBuilders.standaloneSetup(new TaskController(tasks)).build();
        mvc.perform(post("/workflow/task/resolve").contentType("application/json")
                .content("{\"taskId\":\"" + task + "\",\"requestId\":\"" + UUID.randomUUID()
                        + "\",\"comment\":\"已核实\"}"))
                .andExpect(status().isOk());
        assertThat(engine.getTaskService().createTaskQuery().taskId(task).singleResult().getAssignee()).isEqualTo("22");
        assertThatThrownBy(() -> tasks.complete(approve(task, "已归还给原办理人")))
                .isInstanceOf(AccessDeniedException.class);
        login(22L);
        assertThatThrownBy(() -> tasks.resolve(resolve)).isInstanceOf(IllegalArgumentException.class);
        tasks.complete(approve(task, "批准"));
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).extracting("approvalType")
                .containsExactly("delegate", "resolve", "approve");
    }

    @Test
    void resolutionAuditFailureKeepsDelegationPending() {
        var started = start("approval", "resolve-rollback");
        String taskId = taskId(started);
        var delegate = new TaskTransferDTO();
        delegate.setRequestId(UUID.randomUUID().toString());
        delegate.setTaskId(taskId);
        delegate.setTransferUserId(33L);
        login(22L);
        tasks.delegate(delegate);
        jdbc.execute("ALTER TABLE wf_approval_record ADD CONSTRAINT reject_resolution CHECK (approval_type <> 'resolve')");
        var resolve = new TaskResolveDTO();
        resolve.setRequestId(UUID.randomUUID().toString());
        resolve.setTaskId(taskId);
        resolve.setComment("协助完成");
        login(33L);
        assertThatThrownBy(() -> tasks.resolve(resolve)).isInstanceOf(RuntimeException.class);
        var pending = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        assertThat(pending.getAssignee()).isEqualTo("33");
        assertThat(pending.getDelegationState()).isEqualTo(org.flowable.task.api.DelegationState.PENDING);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).extracting("approvalType")
                .containsExactly("delegate");
    }

    @Test
    void delegationAfterPermanentTransferReturnsToNewOwner() {
        var started = start("approval", "delegate-transfer-delegate");
        String taskId = taskId(started);
        var transfer = new TaskTransferDTO();
        transfer.setRequestId(UUID.randomUUID().toString());
        transfer.setTaskId(taskId);
        transfer.setTransferUserId(33L);
        var resolve = new TaskResolveDTO();
        resolve.setRequestId(UUID.randomUUID().toString());
        resolve.setTaskId(taskId);
        resolve.setComment("核实完毕");
        login(22L);
        tasks.delegate(transfer);
        login(33L);
        tasks.resolve(resolve);
        login(22L);
        transfer.setRequestId(UUID.randomUUID().toString());
        tasks.transfer(transfer);
        login(33L);
        transfer.setTransferUserId(44L);
        transfer.setRequestId(UUID.randomUUID().toString());
        tasks.delegate(transfer);
        login(44L);
        resolve.setRequestId(UUID.randomUUID().toString());
        tasks.resolve(resolve);
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult().getAssignee()).isEqualTo("33");
        login(22L);
        assertThatThrownBy(() -> tasks.complete(approve(taskId, "已转走的任务"))).isInstanceOf(AccessDeniedException.class);
        login(33L);
        tasks.complete(approve(taskId, "新办理人批准"));
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("completed");
    }

    @Test
    void delegationAfterReclaimReturnsToNewAssignee() {
        var started = start("approval", "delegate-reclaim-delegate");
        String taskId = taskId(started);
        engine.getTaskService().addCandidateUser(taskId, "22");
        engine.getTaskService().addCandidateUser(taskId, "33");
        var delegate = new TaskTransferDTO();
        delegate.setRequestId(UUID.randomUUID().toString());
        delegate.setTaskId(taskId);
        delegate.setTransferUserId(44L);
        var resolve = new TaskResolveDTO();
        resolve.setRequestId(UUID.randomUUID().toString());
        resolve.setTaskId(taskId);
        resolve.setComment("核实完毕");
        login(22L);
        tasks.delegate(delegate);
        login(44L);
        tasks.resolve(resolve);
        login(22L);
        tasks.unclaim(taskId, UUID.randomUUID().toString());
        login(33L);
        tasks.claim(taskId, 33L, UUID.randomUUID().toString());
        delegate.setRequestId(UUID.randomUUID().toString());
        tasks.delegate(delegate);
        login(44L);
        resolve.setRequestId(UUID.randomUUID().toString());
        tasks.resolve(resolve);
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult().getAssignee()).isEqualTo("33");
        login(33L);
        tasks.complete(approve(taskId, "新办理人批准"));
    }

    @Test
    void commentsUseTaskAndProcessIdsAndTrustedActor() {
        var started = start("approval", "comment");
        TaskCommentDTO comment = new TaskCommentDTO();
        comment.setRequestId(UUID.randomUUID().toString());
        comment.setTaskId(taskId(started));
        comment.setMessage("请补充信息");
        login(33L);
        assertThatThrownBy(() -> tasks.addComment(comment)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tasks.getComments(comment.getTaskId())).isInstanceOf(AccessDeniedException.class);
        login(22L);
        tasks.addComment(comment);
        assertThat(engine.getTaskService().getTaskComments(comment.getTaskId())).singleElement().satisfies(saved -> {
            assertThat(saved.getProcessInstanceId()).isEqualTo(started.getProcessInstanceId());
            assertThat(saved.getUserId()).isEqualTo("22");
        });
        login(11L);
        assertThat(tasks.getComments(comment.getTaskId())).isEqualTo(List.of("请补充信息"));
    }

    @Test
    void onlyStarterCanSuspendResumeOrCancelRunningProcess() {
        var started = start("approval", "lifecycle");
        String task = taskId(started);
        String id = started.getProcessInstanceId();
        login(22L);
        assertThatThrownBy(() -> processes.terminate(id, "越权取消", UUID.randomUUID().toString())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> processes.suspend(id, UUID.randomUUID().toString())).isInstanceOf(AccessDeniedException.class);
        login(11L);
        processes.suspend(id, UUID.randomUUID().toString());
        login(22L);
        assertThatThrownBy(() -> tasks.complete(approve(task, "挂起期间办理"))).isInstanceOf(IllegalArgumentException.class);
        login(11L);
        processes.activate(id, UUID.randomUUID().toString());
        assertThat(processes.getById(id).getStatus()).isEqualTo("running");
        processes.terminate(id, "发起人取消", UUID.randomUUID().toString());
        assertThat(processes.getById(id).getStatus()).isEqualTo("terminated");
        assertThatThrownBy(() -> processes.activate(id, UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processes.terminate(id, "重复取消", UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processLifecycleCommandsReplayAfterStateChanges() {
        var started = start("approval", "lifecycle-idempotent");
        String id = started.getProcessInstanceId();
        login(11L);
        String suspendRequest = UUID.randomUUID().toString();
        assertThat(processes.suspend(id, suspendRequest)).isTrue();
        assertThat(processes.suspend(id, suspendRequest)).isTrue();
        String activateRequest = UUID.randomUUID().toString();
        assertThat(processes.activate(id, activateRequest)).isTrue();
        assertThat(processes.activate(id, activateRequest)).isTrue();
        String terminateRequest = UUID.randomUUID().toString();
        assertThat(processes.terminate(id, "取消", terminateRequest)).isTrue();
        assertThat(processes.terminate(id, "取消", terminateRequest)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation IN ('SUSPEND','ACTIVATE','TERMINATE')",
                Long.class)).isEqualTo(3L);
    }

    @Test
    void urlWriteEndpointsRequireRequestIds() throws Exception {
        var taskMvc = MockMvcBuilders.standaloneSetup(new TaskController(tasks)).build();
        taskMvc.perform(post("/workflow/task/claim").param("taskId", "missing"))
                .andExpect(status().isBadRequest());
        taskMvc.perform(post("/workflow/task/unclaim/missing"))
                .andExpect(status().isBadRequest());

        var processMvc = MockMvcBuilders.standaloneSetup(
                new com.lotus.bixi.workflow.controller.ProcessInstanceController(processes, definitions)).build();
        processMvc.perform(delete("/workflow/process/cancel/missing")).andExpect(status().isBadRequest());
        processMvc.perform(put("/workflow/process/suspend/missing")).andExpect(status().isBadRequest());
        processMvc.perform(put("/workflow/process/activate/missing")).andExpect(status().isBadRequest());
    }

    @Test
    void transferredActorCanReplaySuccessfulTransferWithoutCurrentAssignee() {
        var started = start("approval", "transfer-idempotent");
        String task = taskId(started);
        var transfer = new TaskTransferDTO();
        transfer.setTaskId(task);
        transfer.setTransferUserId(33L);
        transfer.setTransferReason("交接");
        transfer.setRequestId(UUID.randomUUID().toString());
        login(22L);
        tasks.transfer(transfer);
        tasks.transfer(transfer);
        assertThat(processes.getApprovalHistory(started.getProcessInstanceId())).singleElement()
                .extracting("approvalType").isEqualTo("transfer");
    }

    @Test
    void taskViewerCanReadOnlyTheirOwnTaskCommand() {
        var started = start("approval", "task-command-view");
        String task = taskId(started);
        var request = approve(task, "已办理");
        login(22L);
        tasks.complete(request);

        login(22L, List.of("workflow_task_view"));
        assertThat(processes.getCommand(request.getRequestId()).operation()).isEqualTo("COMPLETE");
        login(33L, List.of("workflow_task_view"));
        assertThatThrownBy(() -> processes.getCommand(request.getRequestId()))
                .isInstanceOf(WorkflowCommandNotFoundException.class);
    }

    @Test
    void suspensionWriteFailureRollsBackEngineState() {
        var started = start("approval", "suspend-rollback");
        String requestId = UUID.randomUUID().toString();
        jdbc.execute("ALTER TABLE wf_process_instance ADD CONSTRAINT reject_suspended CHECK (status <> 'suspended')");
        assertThatThrownBy(() -> processes.suspend(started.getProcessInstanceId(), requestId)).isInstanceOf(RuntimeException.class);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.getProcessInstanceId())
                .singleResult().isSuspended()).isFalse();
        assertThat(processes.getById(started.getProcessInstanceId()).getStatus()).isEqualTo("running");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'SUSPEND'", Long.class)).isZero();
        dropCheck("wf_process_instance", "reject_suspended");
        assertThat(processes.suspend(started.getProcessInstanceId(), requestId)).isTrue();
    }

    private void dropCheck(String table, String name) {
        jdbc.execute("ALTER TABLE " + table + (System.getenv("WORKFLOW_TEST_JDBC_URL") == null
                ? " DROP CONSTRAINT " : " DROP CHECK ") + name);
    }

    private ProcessInstanceVO start(String key, String title) {
        if ("demo_leave_approval".equals(key)) {
            return WorkflowTrustedStartTestSupport.start(trustedProcesses, title, 123L, 1);
        }
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setRequestId(java.util.UUID.randomUUID().toString());
        dto.setProcessKey(key);
        dto.setTitle(title);
        dto.setBusinessKey(UUID.randomUUID().toString());
        dto.setVariables(Map.of("approverId", "22"));
        return processes.start(dto);
    }

    private ProcessInstanceVO startWithForm(String title) {
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setProcessKey("approval");
        dto.setTitle(title);
        dto.setBusinessKey(UUID.randomUUID().toString());
        dto.setVariables(Map.of("approverId", "22"));
        dto.setFormId(7L);
        dto.setFormDataJson("{\"amount\":10,\"note\":\"old\",\"secret\":\"classified\"}");
        return processes.start(dto);
    }

    private void bindApprovalForm() {
        var definition = engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("approval").latestVersion().singleResult();
        jdbc.update("INSERT INTO wf_form "
                + "(id, form_key, form_name, current_version, status, del_flag, data_status, tenant_id) "
                + "VALUES (7, 'expense', 'Expense', 1, '1', '0', '0', 1)");
        jdbc.update("INSERT INTO wf_form_version "
                + "(id, form_id, version, schema_json, is_active, del_flag, status, data_status, tenant_id) "
                + "VALUES (71, 7, 1, ?, '1', '0', '0', '0', 1)", FORM_SCHEMA);
        jdbc.update("INSERT INTO wf_process_definition "
                + "(id, process_definition_id, process_key, version, form_key, form_version_id, "
                + "suspension_state, del_flag, status, data_status, tenant_id) "
                + "VALUES (81, ?, 'approval', ?, 'expense', 71, 1, '0', '0', '0', 1)",
                definition.getId(), definition.getVersion());
        insertFieldPermission(101L, definition.getId(), "amount", "readonly");
        insertFieldPermission(102L, definition.getId(), "note", "edit");
        insertFieldPermission(103L, definition.getId(), "secret", "hidden");
    }

    private void insertFieldPermission(long id, String definitionId, String field, String permission) {
        jdbc.update("INSERT INTO sys_form_permission "
                + "(id, form_id, form_version_id, process_definition_id, task_definition_key, field_code, "
                + "permission, perm_type, del_flag, status, data_status, tenant_id) "
                + "VALUES (?, 7, 71, ?, 'review', ?, ?, ?, '0', '0', '0', 1)",
                id, definitionId, field, permission, permission);
        jdbc.update("INSERT INTO sys_role_form_permission "
                + "(id, role_id, form_perm_id, del_flag, status, data_status, tenant_id) "
                + "VALUES (?, 9, ?, '0', '0', '0', 1)", 1000L + id, id);
    }

    private String taskId(ProcessInstanceVO started) {
        return engine.getTaskService().createTaskQuery().processInstanceId(started.getProcessInstanceId()).singleResult().getId();
    }

    private ProcessInstanceVO startBusiness(String key) {
        return startBusiness(key, 2);
    }

    private ProcessInstanceVO startBusiness(String key, int round) {
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addString("demo_leave_approval.bpmn20.xml",
                        model("demo_leave_approval", !"immediate".equals(key)))
                .deploy();
        return WorkflowTrustedStartTestSupport.start(trustedProcesses, key, 123L, round);
    }

    static class ResultReceiver implements WorkflowResultReceiver {
        final java.util.List<WorkflowResultDTO> results = new java.util.ArrayList<>();
        final JdbcTemplate jdbc;
        final org.springframework.transaction.support.TransactionTemplate transaction;
        boolean fail;
        ResultReceiver(DataSource dataSource) {
            jdbc = new JdbcTemplate(dataSource);
            transaction = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Override
        public R<Void> receive(WorkflowResultDTO result) {
            if (fail) throw new IllegalStateException("simulated business service outage");
            // A separate transaction must observe the terminal extension before delivery.
            transaction.executeWithoutResult(ignored -> assertThat(jdbc.queryForObject(
                    "SELECT status FROM wf_process_instance WHERE process_instance_id = ?",
                    String.class, result.getProcessInstanceId())).isEqualTo(result.getStatus()));
            results.add(result);
            return R.ok();
        }
    }

    private static TaskCompleteDTO approve(String task, String comment) {
        TaskCompleteDTO dto = new TaskCompleteDTO();
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setTaskId(task);
        dto.setApprovalComment(comment);
        return dto;
    }

    private static TaskCompleteDTO approveWithRequestId(String task, String comment, String requestId) {
        TaskCompleteDTO dto = approve(task, comment);
        dto.setRequestId(requestId);
        return dto;
    }

    private static TaskCompleteDTO completionWithForm(String taskId, Long formId, String dataJson) {
        TaskCompleteDTO dto = approve(taskId, "通过");
        dto.setFormId(formId);
        dto.setFormDataJson(dataJson);
        return dto;
    }

    static void login(long id) {
        login(id, List.of("workflow_process_add", "workflow_process_view", "workflow_process_edit",
                "workflow_task_view", "workflow_task_edit", "workflow_definition_view", "workflow_definition_edit"));
    }

    static void login(long id, List<String> permissions) {
        TenantContextHolder.set(1L);
        var authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
        var user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null, true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    private static void loginWithRole(long id, long roleId) {
        login(id, List.of("workflow_process_view", "workflow_task_view", "workflow_task_edit", "ROLE_" + roleId));
    }

    private static Object runAs(long userId, Runnable action, String success) {
        login(userId);
        try {
            action.run();
            return success;
        } catch (RuntimeException error) {
            return error;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Aspect
    static class CommandBarrier {
        volatile CyclicBarrier commandBarrier;
        volatile CyclicBarrier processBarrier;
        volatile String replayMissRequestId;
        volatile CountDownLatch replayMissObserved;
        volatile CountDownLatch replayMissRelease;
        final AtomicBoolean replayMissBlocked = new AtomicBoolean();

        void blockAfterInitialReplayMiss(String requestId) {
            replayMissRequestId = requestId;
            replayMissObserved = new CountDownLatch(1);
            replayMissRelease = new CountDownLatch(1);
            replayMissBlocked.set(false);
        }

        void clearReplayMiss() {
            replayMissRequestId = null;
            replayMissObserved = null;
            CountDownLatch release = replayMissRelease;
            replayMissRelease = null;
            if (release != null) release.countDown();
            replayMissBlocked.set(false);
        }

        @Around("execution(* com.lotus.bixi.workflow.command.WorkflowCommandTransaction.find(..))")
        Object replayMissRendezvous(ProceedingJoinPoint invocation) throws Throwable {
            Object result = invocation.proceed();
            String target = replayMissRequestId;
            Object[] args = invocation.getArgs();
            if (result == null && target != null && args.length == 3 && target.equals(args[2])
                    && replayMissBlocked.compareAndSet(false, true)) {
                replayMissObserved.countDown();
                if (!replayMissRelease.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for the same request winner");
                }
            }
            return result;
        }

        @Around("execution(* com.lotus.bixi.workflow.command.WorkflowCommandTransaction.executeNew(..))")
        Object commandRendezvous(ProceedingJoinPoint invocation) throws Throwable {
            CyclicBarrier waiting = commandBarrier;
            if (waiting != null) waiting.await(10, TimeUnit.SECONDS);
            return invocation.proceed();
        }

        @Around("execution(* com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper.selectForUpdate(..))")
        Object processRendezvous(ProceedingJoinPoint invocation) throws Throwable {
            CyclicBarrier waiting = processBarrier;
            if (waiting != null) waiting.await(10, TimeUnit.SECONDS);
            return invocation.proceed();
        }
    }

    static String model(String key, boolean human) {
        String task = human ? """
                <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="审批" flowable:assignee="${approverId}"/>
                <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                """ : "<sequenceFlow id=\"toEnd\" sourceRef=\"start\" targetRef=\"end\"/>";
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.example/test">
                  <process id="%s" isExecutable="true"><startEvent id="start"/>%s
                    <endEvent id="end"><extensionElements>
                      <flowable:executionListener event="end" delegateExpression="${processEndListener}"/>
                    </extensionElements></endEvent>
                  </process>
                </definitions>
                """.formatted(key, task);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @EnableMethodSecurity
    @ComponentScan({"com.lotus.bixi.workflow.service.impl", "com.lotus.bixi.workflow.command", "com.lotus.bixi.workflow.listener"})
    @MapperScan("com.lotus.bixi.workflow.mapper")
    @Import({MybatisAutoConfiguration.class, WorkflowEngineEventsConfiguration.class, ProcessDefinitionController.class})
    @ImportAutoConfiguration({com.lotus.bixi.common.core.config.JacksonConfiguration.class,
            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
            WorkflowAutoConfiguration.class, ProcessEngineAutoConfiguration.class,
            ProcessEngineServicesAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    static class Config {
        @Bean DataSource dataSource() {
            String mysqlUrl = System.getenv("WORKFLOW_TEST_JDBC_URL");
            if (mysqlUrl != null && !mysqlUrl.isBlank()) {
                return new DriverManagerDataSource(mysqlUrl, System.getenv("WORKFLOW_TEST_DB_USER"),
                        System.getenv("WORKFLOW_TEST_DB_PASSWORD"));
            }
            return new DriverManagerDataSource("jdbc:h2:mem:workflow-approval-" + UUID.randomUUID()
                    + ";MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ResultReceiver resultReceiver(DataSource source) { return new ResultReceiver(source); }
        @Bean CommandBarrier commandBarrier() { return new CommandBarrier(); }
        @Bean WorkflowTaskNotificationSink taskNotifications() { return mock(WorkflowTaskNotificationSink.class); }
        @Bean CandidateIdentityQueryService candidateIdentityQueryService() {
            return userId -> userId == null || userId <= 0 ? null : new CandidateIdentity(userId, true, false, 1L);
        }
        @Bean CandidateRoleQueryService candidateRoleQueryService() {
            return roleId -> roleId == null || roleId <= 0 ? null : new CandidateRole(roleId, true, 1L);
        }
        @Bean("pms") PermissionService permissionService() { return new PermissionService(); }
        @Bean static PrePostTemplateDefaults prePostTemplateDefaults() { return new PrePostTemplateDefaults(); }
    }
}
