package com.lotus.bixi.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException;
import com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.command.WorkflowCommandExecutor;
import com.lotus.bixi.workflow.controller.ProcessInstanceController;
import com.lotus.bixi.workflow.controller.WorkflowCommandController;
import com.lotus.bixi.workflow.controller.WorkflowCommandExceptionAdvice;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mybatis.spring.SqlSessionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.lotus.bixi.workflow.service.WorkflowApprovalIntegrationTest.login;
import static com.lotus.bixi.workflow.service.WorkflowApprovalIntegrationTest.model;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real services, database constraints and engine transactions; also runs on disposable MySQL. */
@SpringJUnitConfig(WorkflowStartIdempotencyTest.Config.class)
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {"workflow.enabled=true",
        "workflow.database-schema-update=true", "workflow.async-executor-activate=false",
        "flowable.check-process-definitions=false"})
class WorkflowStartIdempotencyTest {
    @Autowired ObjectMapper applicationJson;
    @Autowired ProcessInstanceService processes;
    @Autowired ProcessDefinitionService definitions;
    @Autowired WorkflowCommandExecutor commands;
    @Autowired WfTaskService tasks;
    @Autowired ProcessEngine engine;
    @Autowired DataSource source;
    @Autowired DataSourceTransactionManager transactionManager;
    @Autowired SqlSessionTemplate mybatis;
    @Autowired ReservationBarrier reservation;
    @Autowired WorkflowApprovalIntegrationTest.ResultReceiver receiver;
    JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record", "wf_form_data");
        receiver.results.clear();
        receiver.fail = false;
        engine.getRepositoryService().createDeployment()
                .addString("approval.bpmn20.xml", model("approval", true))
                .addString("immediate.bpmn20.xml", model("immediate", false)).deploy();
        login(11L);
    }
    @AfterEach void cleanup() {
        reservation.barrier = null;
        SecurityContextHolder.clearContext();
        engine.getRepositoryService().createDeploymentQuery().list()
                .forEach(d -> engine.getRepositoryService().deleteDeployment(d.getId(), true));
    }

    @Test void commandResponseExactlyMatchesTheApplicationHttpSerialization() throws Exception {
        var dto = request("approval");
        var mvc = MockMvcBuilders.standaloneSetup(new ProcessInstanceController(processes, definitions),
                new WorkflowCommandController(commands)).setMessageConverters(
                    new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(applicationJson)).build();
        var first = mvc.perform(post("/workflow/process/start").contentType("application/json")
                .content(applicationJson.writeValueAsString(dto))).andExpect(status().isOk()).andReturn();
        var lookup = mvc.perform(get("/workflow/command/" + dto.getRequestId()))
                .andExpect(status().isOk()).andReturn();
        var original = applicationJson.readTree(first.getResponse().getContentAsString()).path("data");
        var saved = applicationJson.readTree(lookup.getResponse().getContentAsString()).path("data").path("response");
        assertThat(original.path("id").isTextual()).isTrue();
        assertThat(saved).isEqualTo(original);
    }

    @Test void httpVariablesRejectIsolatedSurrogatesAndKeepTheTaskUnchanged() throws Exception {
        var started = processes.start(request("approval"));
        String taskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult().getId();
        login(22L);
        var mvc = MockMvcBuilders.standaloneSetup(new com.lotus.bixi.workflow.controller.TaskController(tasks))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(applicationJson))
                .setControllerAdvice(new com.lotus.bixi.workflow.controller.WorkflowCommandExceptionAdvice(),
                        new com.lotus.bixi.common.feign.sentinel.handle.GlobalBizExceptionHandler())
                .build();
        mvc.perform(post("/workflow/task/complete").contentType("application/json")
                .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"taskId\":\"" + taskId
                        + "\",\"variables\":{\"text\":\"\\uD800\"}}"))
                .andExpect(status().isBadRequest());
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMPLETE'", Long.class))
                .isZero();
        String badCommentRequestId = UUID.randomUUID().toString();
        mvc.perform(post("/workflow/task/comment").contentType("application/json")
                .content("{\"requestId\":\"" + badCommentRequestId + "\",\"taskId\":\"" + taskId
                        + "\",\"message\":\"\\uD801\"}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> commands.getCommand(badCommentRequestId))
                .isInstanceOf(com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException.class);
        assertThat(engine.getTaskService().getTaskComments(taskId)).isEmpty();
    }

    @Test void invalidUnicodeCommentCannotReplayASavedRequest() {
        var started = processes.start(request("approval"));
        String taskId = engine.getTaskService().createTaskQuery()
                .processInstanceId(started.getProcessInstanceId()).singleResult().getId();
        login(22L);
        String requestId = UUID.randomUUID().toString();
        var original = new com.lotus.bixi.workflow.api.dto.TaskCommentDTO();
        original.setTaskId(taskId);
        original.setRequestId(requestId);
        original.setMessage("原始评论");
        tasks.addComment(original);
        for (String message : List.of("\uD800", "\uD801")) {
            var comment = new com.lotus.bixi.workflow.api.dto.TaskCommentDTO();
            comment.setTaskId(taskId);
            comment.setRequestId(requestId);
            comment.setMessage(message);
            assertThatThrownBy(() -> tasks.addComment(comment)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(engine.getTaskService().getTaskComments(taskId)).singleElement()
                .extracting(org.flowable.engine.task.Comment::getFullMessage).isEqualTo("原始评论");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'COMMENT'", Long.class))
                .isEqualTo(1L);
    }

    @Test void originalSnapshotSurvivesCompletionAndRedeployment() {
        var dto = request("approval");
        var first = processes.start(dto);
        login(22L);
        var complete = new TaskCompleteDTO();
        complete.setRequestId(UUID.randomUUID().toString());
        complete.setTaskId(engine.getTaskService().createTaskQuery().processInstanceId(first.getProcessInstanceId()).singleResult().getId());
        tasks.complete(complete);
        engine.getRepositoryService().createDeployment()
                .addString("approval.bpmn20.xml", model("approval", false)).deploy();
        login(11L);
        assertThat(processes.getById(first.getProcessInstanceId()).getStatus()).isEqualTo("completed");
        assertThat(processes.start(dto)).usingRecursiveComparison().isEqualTo(first);
        assertThat(commands.getCommand(dto.getRequestId()).response().get("status").textValue()).isEqualTo("running");
        assertCounts(1, 0, 1);
    }

    @Test void normalizedFormNumbersAndOverwrittenIdentityReplay() {
        var dto = request("approval");
        dto.setFormId(99L);
        dto.setFormDataJson("{\"b\":1.0,\"a\":{\"字\":true}}");
        dto.setVariables(Map.of("approverId", "22", "value", 1.0, "applicant", "forged", "tenantId", "forged"));
        var first = processes.start(dto);
        dto.setFormDataJson(" {\"a\":{\"字\":true},\"b\":1e0} ");
        dto.setVariables(Map.of("value", 1, "approverId", "22", "startUserId", "another"));
        assertThat(processes.start(dto)).usingRecursiveComparison().isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT data_json FROM wf_form_data", String.class))
                .isEqualTo("{\"a\":{\"字\":true},\"b\":1}");
        assertThat(engine.getRuntimeService().getVariable(first.getProcessInstanceId(), "applicant")).isEqualTo("11");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form_data", Long.class)).isEqualTo(1);
    }

    @Test void localAndFeignEncodedNumericVariablesShareTheSameCommand() throws Exception {
        var local = request("approval");
        local.setVariables(Map.of("approverId", "22", "amount", 9007199254740993L));
        var remote = applicationJson.readValue(applicationJson.writeValueAsString(local), ProcessStartDTO.class);
        var first = processes.start(local);
        assertThat(processes.start(remote)).usingRecursiveComparison().isEqualTo(first);
        assertThat(engine.getRuntimeService().getVariable(first.getProcessInstanceId(), "amount").toString())
                .isEqualTo("9007199254740993");
        assertCounts(1, 1, 1);
    }

    @Test void distinctHttpDecimalsCannotReplayAsTheSameContent() throws Exception {
        var dto = request("approval");
        String wire = applicationJson.writeValueAsString(dto);
        var one = applicationJson.readValue(wire.replace("\"approverId\":\"22\"", "\"approverId\":\"22\",\"amount\":9007199254740992.0"), ProcessStartDTO.class);
        var two = applicationJson.readValue(wire.replace("\"approverId\":\"22\"", "\"approverId\":\"22\",\"amount\":9007199254740993.0"), ProcessStartDTO.class);
        processes.start(one);
        assertThatThrownBy(() -> processes.start(two)).isInstanceOf(WorkflowRequestConflictException.class);
        assertCounts(1, 1, 1);
    }

    @Test void unrelatedUniqueConstraintFailureIsNotACommandReplay() {
        var first = request("approval"); processes.start(first);
        jdbc.execute("ALTER TABLE wf_process_instance ADD CONSTRAINT unique_test_title UNIQUE (title)");
        var other = request("approval");
        assertThatThrownBy(() -> processes.start(other)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> commands.getCommand(other.getRequestId())).isInstanceOf(WorkflowCommandNotFoundException.class);
        assertCounts(1, 1, 1);
    }

    @Test void changedTitleVariablesRoundOrOperationConflict() {
        var original = request("approval");
        processes.start(original);
        for (java.util.function.Consumer<ProcessStartDTO> change : List.<java.util.function.Consumer<ProcessStartDTO>>of(
                dto -> dto.setTitle("changed"), dto -> dto.setVariables(Map.of("approverId", "33")),
                dto -> dto.setVariables(Map.of("approverId", "22", "businessRound", 2)), dto -> dto.setProcessKey("immediate"))) {
            var changed = request("approval");
            org.springframework.beans.BeanUtils.copyProperties(original, changed);
            change.accept(changed);
            assertThatThrownBy(() -> processes.start(changed)).isInstanceOf(WorkflowRequestConflictException.class);
        }
        assertCounts(1, 1, 1);
    }

    @Test void malformedRequestNeverReservesCommand() throws Exception {
        var controller = new ProcessInstanceController(processes, definitions);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String id : List.of("", "00000000-0000-0000-0000-00000000000A", "bad")) {
            mvc.perform(post("/workflow/process/start").contentType("application/json")
                    .content("{\"processKey\":\"approval\",\"requestId\":\"" + id + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        var invalid = request("approval"); invalid.setRequestId(null);
        assertThatThrownBy(() -> processes.start(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setRequestId(UUID.randomUUID().toString());
        invalid.setVariables(Map.of("unserializable", new Object()));
        assertThatThrownBy(() -> processes.start(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertCounts(0, 0, 0);
    }

    @Test void currentPermissionAndActorScopeApplyBeforeReplayOrLookup() {
        var dto = request("approval");
        processes.start(dto);
        login(11L, List.of("workflow_process_view"));
        assertThatThrownBy(() -> processes.start(dto)).isInstanceOf(AccessDeniedException.class);
        login(11L, List.of("workflow_process_add"));
        assertThatThrownBy(() -> commands.getCommand(dto.getRequestId())).isInstanceOf(AccessDeniedException.class);
        login(33L);
        assertThatThrownBy(() -> commands.getCommand(dto.getRequestId())).isInstanceOf(WorkflowCommandNotFoundException.class);
        // The request key belongs to an actor scope; another actor may use the same UUID for their own intention.
        assertThat(processes.start(dto).getStartUserId()).isEqualTo(33L);
        assertCounts(2, 2, 2);
    }

    @Test void httpConflictAndNotFoundUseStableActorScopedEnvelopes() throws Exception {
        var dto = request("approval"); processes.start(dto); dto.setTitle("changed");
        var mvc = MockMvcBuilders.standaloneSetup(new ProcessInstanceController(processes, definitions),
                new WorkflowCommandController(commands)).setControllerAdvice(new WorkflowCommandExceptionAdvice()).build();
        mvc.perform(post("/workflow/process/start").contentType("application/json")
                .content(new ObjectMapper().writeValueAsString(dto))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("WORKFLOW_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.data.requestId").value(dto.getRequestId()));
        login(33L);
        mvc.perform(get("/workflow/command/" + dto.getRequestId())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value("WORKFLOW_COMMAND_NOT_FOUND"));
    }

    @Test void simultaneousSameContentStartsExactlyOneInstance() throws Exception {
        var dto = request("approval");
        var results = race(dto, dto);
        assertThat(results.get(0)).isInstanceOf(ProcessInstanceVO.class);
        assertThat(results.get(1)).usingRecursiveComparison().isEqualTo(results.get(0));
        assertThat(reservation.collisions).hasValue(1);
        assertCounts(1, 1, 1);
    }
    @Test void simultaneousDifferentContentHasOneCommittedWinnerAndOneConflict() throws Exception {
        var one = request("approval");
        var two = request("approval");
        org.springframework.beans.BeanUtils.copyProperties(one, two); two.setTitle("conflicting intent");
        var results = race(one, two);
        assertThat(results.stream().filter(ProcessInstanceVO.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(WorkflowRequestConflictException.class::isInstance)).hasSize(1);
        assertThat(reservation.collisions).hasValue(1);
        assertCounts(1, 1, 1);
    }

    @Test void immediateReplayDeliversOnlyOneTerminalResult() {
        var dto = request("immediate");
        dto.setBusinessTable("demo_leave_request"); dto.setBusinessId(1L);
        dto.setVariables(Map.of("businessRound", 1));
        var first = processes.start(dto);
        assertThat(processes.start(dto)).usingRecursiveComparison().isEqualTo(first);
        assertThat(first.getStatus()).isEqualTo("completed");
        assertThat(receiver.results).hasSize(1);
        assertCounts(1, 0, 1);
    }

    @Test void engineExtensionFormAndCommandUseTheSameTransaction() {
        var config = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        assertThat(config.getTransactionManager()).isSameAs(transactionManager);
        assertThat(((org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy) config.getDataSource())
                .getTargetDataSource()).isSameAs(source);
        assertThat(mybatis.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource()).isSameAs(source);
        assertThat(transactionManager.getDataSource()).isSameAs(source);
    }

    @Test void extensionFailureRollsBackEngineAndAllowsSameIdRetry() {
        var dto = request("approval");
        jdbc.execute("ALTER TABLE wf_process_instance ADD CONSTRAINT fail_extension CHECK (title <> 'intention')");
        assertThatThrownBy(() -> processes.start(dto)).isInstanceOf(RuntimeException.class);
        assertCounts(0, 0, 0);
        dropCheck("wf_process_instance", "fail_extension");
        processes.start(dto);
        assertCounts(1, 1, 1);
    }
    @Test void formFailureRollsBackEngineExtensionAndCommand() {
        var dto = request("approval"); dto.setFormId(99L); dto.setFormDataJson("{\"days\":2}");
        jdbc.execute("ALTER TABLE wf_form_data ADD CONSTRAINT fail_form CHECK (form_id <> 99)");
        assertThatThrownBy(() -> processes.start(dto)).isInstanceOf(RuntimeException.class);
        assertCounts(0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form_data", Long.class)).isZero();
        dropCheck("wf_form_data", "fail_form");
        processes.start(dto); assertCounts(1, 1, 1);
    }
    @Test void commandResultFailureRollsBackAllEngineAndBusinessWrites() {
        var dto = request("approval"); dto.setFormId(99L); dto.setFormDataJson("{\"days\":2}");
        jdbc.execute("ALTER TABLE wf_command ADD CONSTRAINT fail_command_result CHECK (status <> 'SUCCEEDED')");
        assertThatThrownBy(() -> processes.start(dto)).isInstanceOf(RuntimeException.class);
        assertCounts(0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form_data", Long.class)).isZero();
        dropCheck("wf_command", "fail_command_result");
        processes.start(dto); assertCounts(1, 1, 1);
    }

    private void dropCheck(String table, String name) {
        jdbc.execute("ALTER TABLE " + table + (System.getenv("WORKFLOW_TEST_JDBC_URL") == null ? " DROP CONSTRAINT " : " DROP CHECK ") + name);
    }
    private void assertCounts(long command, long runtime, long history) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_command WHERE operation = 'START'", Long.class))
                .isEqualTo(command);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_process_instance", Long.class)).isEqualTo(command);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().count()).isEqualTo(runtime);
        assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery().count()).isEqualTo(history);
    }
    private ProcessStartDTO request(String key) {
        var dto = new ProcessStartDTO(); dto.setRequestId(UUID.randomUUID().toString());
        dto.setProcessKey(key); dto.setBusinessKey(UUID.randomUUID().toString()); dto.setTitle("intention");
        dto.setVariables(Map.of("approverId", "22")); return dto;
    }
    private List<Object> race(ProcessStartDTO one, ProcessStartDTO two) throws Exception {
        reservation.collisions.set(0); reservation.barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (var dto : List.of(one, two)) futures.add(pool.submit(() -> {
                login(11L);
                try { return processes.start(dto); }
                catch (RuntimeException e) { return e; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { reservation.barrier = null; pool.shutdownNow(); }
    }
    @Aspect
    static class ReservationBarrier {
        volatile CyclicBarrier barrier;
        final AtomicInteger collisions = new AtomicInteger();
        @Around("execution(* com.lotus.bixi.workflow.command.WorkflowCommandTransaction.executeNew(..))")
        Object rendezvous(ProceedingJoinPoint invocation) throws Throwable {
            var waiting = barrier;
            if (waiting != null) waiting.await(10, TimeUnit.SECONDS);
            try { return invocation.proceed(); }
            catch (RuntimeException error) {
                if (error.getClass().getSimpleName().equals("RequestKeyCollision")) collisions.incrementAndGet();
                throw error;
            }
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Import(WorkflowApprovalIntegrationTest.Config.class)
    @EnableAspectJAutoProxy
    static class Config {
        @Bean ReservationBarrier reservationBarrier() { return new ReservationBarrier(); }
    }
}
