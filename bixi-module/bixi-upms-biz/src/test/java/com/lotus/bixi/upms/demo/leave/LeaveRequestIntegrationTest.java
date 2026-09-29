package com.lotus.bixi.upms.demo.leave;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.ReliableDeliveryProperties;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.service.SysUserService;
import com.lotus.bixi.upms.demo.leave.dto.LeaveRequestDTO;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventPublisher;
import com.lotus.bixi.upms.demo.leave.service.LeaveRequestService;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.lotus.bixi.common.feign.sentinel.handle.GlobalBizExceptionHandler;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MyBatis, transactions, canonical schema and domain service. Only external contracts are mocked. */
@org.springframework.test.context.event.RecordApplicationEvents
@SpringJUnitConfig(LeaveRequestIntegrationTest.Config.class)
@TestPropertySource(properties = {"workflow.enabled=true", "bixi.reliable.enabled=true",
        "bixi.deployment.mode=cloud", "mybatis-plus.global-config.banner=false"})
public class LeaveRequestIntegrationTest {
    @Autowired LeaveRequestService leaves;
    @Autowired com.lotus.bixi.upms.demo.leave.controller.LeaveWorkflowResultController internalController;
    @Autowired org.springframework.mock.web.MockHttpServletRequest innerRequest;
    @Autowired com.lotus.bixi.upms.demo.leave.controller.LeaveRequestController controller;
    @Autowired org.springframework.test.context.event.ApplicationEvents events;
    @Autowired WorkflowService workflows;
    @Autowired SysUserService users;
    @Autowired DataSource source;
    @Autowired com.lotus.bixi.upms.mapper.SysUserMapper userMapper;
    JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.set(1L);
        reset(workflows, users);
        innerRequest.removeHeader(com.lotus.bixi.common.core.constant.SecurityConstants.FROM);
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            if ("H2".equals(connection.getMetaData().getDatabaseProductName())) {
                jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                        + LeaveRequestIntegrationTest.class.getName() + ".utcTimestamp'");
            }
        }
        recreateTable("demo_leave_request");
        recreateTable("demo_leave_command");
        recreateTable("reliable_outbox");
        SysUser user = new SysUser();
        user.setId(22L); user.setLockFlag("0"); user.setDelFlag("0"); user.setStatus("0");
        when(users.getById(22L)).thenReturn(user);
        login(11L);
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test void configuredDatabaseUsesRequestedEngineAndMysqlDefaultIsolation() throws Exception {
        try (var connection = source.getConnection()) {
            String mysqlUrl = System.getenv("WORKFLOW_TEST_JDBC_URL");
            if (mysqlUrl != null && !mysqlUrl.isBlank()) {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
                assertThat(connection.getCatalog()).isEqualTo("workflow_approval_test");
                assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            }
            else {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            }
        }
    }

    @Test void approverLookupFiltersActiveUsersAndReturnsOnlySafeProjection() throws Exception {
        recreateTable("sys_user");
        for (long id : List.of(11L, 22L, 33L, 44L, 55L)) {
            jdbc.update("INSERT INTO sys_user(id,tenant_id,username,name,password,lock_flag,status,del_flag) VALUES (?,?,?,?,?,?,?,?)",
                    id, 1L, "user-" + id, "审批人" + id, "never-expose", id == 33 ? "9" : "0", id == 44 ? "1" : "0", id == 55 ? "1" : "0");
        }
        doAnswer(call -> userMapper.selectPage(call.getArgument(0), call.getArgument(1)))
                .when(users).page(any(Page.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        var page = leaves.approvers(1, 10, "审批人");
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement().satisfies(user -> {
            assertThat(user.id()).isEqualTo(22L);
            assertThat(user.name()).isEqualTo("审批人22");
            assertThat(user.username()).isEqualTo("user-22");
        });
        var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(page.getRecords());
        assertThat(json).doesNotContain("password", "salt", "phone", "never-expose");
        assertThat(leaves.approvers(1, 10, "absent").getRecords()).isEmpty();
        assertThatThrownBy(() -> leaves.approvers(1, 101, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void actualControllerRejectsInvalidInputAndCannotMassAssignServerFields() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/demo/leave")
                .contentType("application/json").content("{\"approverId\":22,\"reason\":\" \"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/demo/leave")
                .contentType("application/json").content("""
                {"approverId":22,"startDate":"2026-10-01","endDate":"2026-10-02","reason":"家事",
                 "id":99,"applicantId":33,"leaveStatus":"APPROVED","businessKey":"forged","round":9,
                 "processInstanceId":"forged","createBy":33}
                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var saved = leaves.page(new Page<>(1, 10), null).getRecords().get(0);
        assertThat(saved.getId()).isNotEqualTo(99L);
        assertThat(saved.getApplicantId()).isEqualTo(11L);
        assertThat(saved.getCreateBy()).isEqualTo(11L);
        assertThat(saved.getLeaveStatus()).isEqualTo("DRAFT");
        assertThat(saved.getRound()).isEqualTo(1);
        assertThat(saved.getBusinessKey()).isNotEqualTo("forged");
        assertThat(saved.getProcessInstanceId()).isNull();
    }

    @Test void clientInvalidLeaveStatesReturnBusinessFailureOverHttp() throws Exception {
        var draft = leaves.create(request());
        var mvc = httpWithProjectErrors();
        mvc.perform(post("/demo/leave/{id}/refresh", draft.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("尚无已确认的流程绑定，不能自动重试，请联系管理员核查"));
        leaves.submit(draft.getId(), UUID.randomUUID().toString());
        mvc.perform(post("/demo/leave/{id}/submit", draft.getId())
                        .param("requestId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("请假申请已提交，不能使用新的requestId重复提交"));
        mvc.perform(put("/demo/leave/{id}", draft.getId()).contentType("application/json").content("""
                {"approverId":22,"startDate":"2026-10-01","endDate":"2026-10-02","reason":"修改"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(delete("/demo/leave/{id}", draft.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("SUBMITTING");
        verify(workflows, never()).startProcess(any());
    }

    @Test void submitRequiresAStableClientRequestIdAndReplaysTheSavedResponse() throws Exception {
        var draft = leaves.create(request());
        var mvc = httpWithProjectErrors();
        mvc.perform(post("/demo/leave/{id}/submit", draft.getId()))
                .andExpect(status().isBadRequest());

        String requestId = UUID.randomUUID().toString();
        LeaveRequest first = leaves.submit(draft.getId(), requestId);
        LeaveRequest replay = leaves.submit(draft.getId(), requestId);

        assertThat(replay).usingRecursiveComparison().isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_command", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT client_request_id FROM demo_leave_command", String.class))
                .isEqualTo(requestId);
        assertThat(jdbc.queryForObject("SELECT status FROM demo_leave_command", String.class)).isEqualTo("ACCEPTED");
        assertThat(first.getLeaveStatus()).isEqualTo("SUBMITTING");
        assertThat(first.getProcessInstanceId()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox", Long.class)).isEqualTo(1L);
        verify(workflows, never()).startProcess(any());

        jdbc.update("UPDATE demo_leave_command SET status = 'STARTED', process_instance_id = 'p-1'");
        assertThat(leaves.submit(draft.getId(), requestId)).usingRecursiveComparison().isEqualTo(first);
        jdbc.update("UPDATE demo_leave_command SET status = 'REJECTED', error_code = 'INVALID_START'");
        assertThat(leaves.submit(draft.getId(), requestId)).usingRecursiveComparison().isEqualTo(first);
        verify(workflows, never()).startProcess(any());

        var otherDraft = leaves.create(request());
        assertThatThrownBy(() -> leaves.submit(otherDraft.getId(), requestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
        verify(workflows, never()).startProcess(any());
    }

    @Test void outboxFailureRollsBackAndTheSameRequestCanRecover() throws Exception {
        var draft = leaves.create(request());
        String requestId = UUID.randomUUID().toString();
        jdbc.execute("DROP TABLE reliable_outbox");
        assertThatThrownBy(() -> leaves.submit(draft.getId(), requestId))
                .isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_command", Long.class)).isZero();
        recreateTable("reliable_outbox");
        assertThat(leaves.submit(draft.getId(), requestId).getLeaveStatus()).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox", Long.class)).isEqualTo(1L);
        verify(workflows, never()).startProcess(any());
    }

    @Test void actualControllerEnforcesPermissionsAndEmitsTrustedWriteAuditEvents() {
        var draft = controller.create(request()).getData();
        controller.update(draft.getId(), request());
        controller.delete(draft.getId());
        var logged = events.stream(com.lotus.bixi.common.log.event.SysLogEvent.class)
                .map(event -> (com.lotus.bixi.upms.api.entity.SysLog) event.getSource()).toList();
        assertThat(logged).extracting(com.lotus.bixi.upms.api.entity.SysLog::getTitle)
                .contains("新增请假申请", "修改请假申请", "删除请假申请");
        assertThat(logged).allSatisfy(log -> assertThat(log.getCreateBy()).isEqualTo(11L));
        login(11L, List.of());
        assertThatThrownBy(() -> controller.create(request())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void createsDraftWithServerIdentityAndSupportsActualCrudPagination() {
        var draft = leaves.create(request());
        assertThat(draft.getId()).isPositive();
        assertThat(draft.getApplicantId()).isEqualTo(11L);
        assertThat(draft.getLeaveStatus()).isEqualTo("DRAFT");
        assertThat(draft.getRound()).isEqualTo(1);
        assertThat(draft.getBusinessKey()).isNotBlank();
        assertThat(draft.getProcessInstanceId()).isNull();
        assertThat(draft.getCreateBy()).isEqualTo(11L);
        var edit = request(); edit.setReason("修改原因");
        assertThat(leaves.update(draft.getId(), edit).getReason()).isEqualTo("修改原因");
        assertThat(leaves.page(new Page<>(1, 10), "DRAFT").getTotal()).isEqualTo(1);
        leaves.delete(draft.getId());
        assertThat(leaves.page(new Page<>(1, 10), null).getRecords()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT del_flag FROM demo_leave_request WHERE id=?", String.class, draft.getId())).isEqualTo("1");
    }

    @Test void validatesDateRangeAndActiveApproverBeforeWriting() {
        var badDate = request(); badDate.setEndDate(badDate.getStartDate().minusDays(1));
        assertThatThrownBy(() -> leaves.create(badDate)).isInstanceOf(IllegalArgumentException.class);
        var missing = request(); missing.setApproverId(999L);
        assertThatThrownBy(() -> leaves.create(missing)).isInstanceOf(IllegalArgumentException.class);
        users.getById(22L).setLockFlag("9");
        assertThatThrownBy(() -> leaves.create(request())).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_request", Integer.class)).isZero();
    }

    @Test void rejectsInvalidDtoAtServiceBoundary() {
        var invalid = request(); invalid.setReason(" ");
        assertThatThrownBy(() -> leaves.create(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setReason("x".repeat(1001));
        assertThatThrownBy(() -> leaves.create(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void callerCanOnlySeeOwnListAndCannotReadAnotherDraft() {
        var draft = leaves.create(request()); login(33L);
        assertThat(leaves.page(new Page<>(1, 10), null).getRecords()).isEmpty();
        assertThatThrownBy(() -> leaves.details(draft.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.update(draft.getId(), request())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.delete(draft.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.submit(draft.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void directServiceCallsEnforcePermissions() {
        var draft = leaves.create(request()); login(11L, List.of());
        assertThatThrownBy(() -> leaves.create(request())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.details(draft.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.submit(draft.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void submissionCommandBusinessStateAndOutboxShareOneLocalTransaction() {
        var draft = leaves.create(request());
        String requestId = UUID.randomUUID().toString();
        var submitted = leaves.submit(draft.getId(), requestId);
        assertThat(submitted.getLeaveStatus()).isEqualTo("SUBMITTING");
        assertThat(submitted.getProcessInstanceId()).isNull();
        assertThat(submitted.getSubmittedAt()).isNotNull();
        String commandId = jdbc.queryForObject("SELECT command_id FROM demo_leave_command", String.class);
        assertThat(commandId).matches("[0-9a-f-]{36}");
        assertThat(jdbc.queryForObject("SELECT start_command_id FROM demo_leave_request WHERE id=?",
                String.class, draft.getId())).isEqualTo(commandId);
        assertThat(jdbc.queryForObject("SELECT event_id FROM reliable_outbox", String.class))
                .isEqualTo(UUID.nameUUIDFromBytes(("leave:event:" + draft.getId() + ":1:start")
                        .getBytes(StandardCharsets.UTF_8)).toString());
        String payload = jdbc.queryForObject("SELECT payload_json FROM reliable_outbox", String.class);
        var event = new WorkflowEventCodec().decode(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(event.commandId()).isEqualTo(commandId);
        assertThat(event.businessId()).isEqualTo(draft.getId());
        assertThat(event.businessKey()).isEqualTo(draft.getBusinessKey());
        assertThat(event.tenantScope()).isEqualTo("1");
        assertThatThrownBy(() -> leaves.submit(draft.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leaves.update(draft.getId(), request())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leaves.delete(draft.getId())).isInstanceOf(IllegalArgumentException.class);
        verify(workflows, never()).startProcess(any());
    }

    @Test void outboxRetryKeepsTheStableCommandAndEventIdentity() throws Exception {
        var draft = leaves.create(request());
        String requestId = UUID.randomUUID().toString();
        jdbc.execute("DROP TABLE reliable_outbox");
        assertThatThrownBy(() -> leaves.submit(draft.getId(), requestId)).isInstanceOf(RuntimeException.class);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("DRAFT");
        recreateTable("reliable_outbox");
        assertThat(leaves.submit(draft.getId(), requestId).getLeaveStatus()).isEqualTo("SUBMITTING");
        String commandId = jdbc.queryForObject("SELECT command_id FROM demo_leave_command", String.class);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM reliable_outbox", String.class))
                .contains("\"commandId\":\"" + commandId + "\"");
        assertThat(leaves.submit(draft.getId(), requestId).getStartCommandId()).isEqualTo(commandId);
        verify(workflows, never()).startProcess(any());
    }

    @Test void duplicateConcurrentSubmitStartsAtMostOneProcess() throws Exception {
        var draft = leaves.create(request());
        CountDownLatch ready = new CountDownLatch(2), begin = new CountDownLatch(1);
        String requestId = UUID.randomUUID().toString();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<LeaveRequest> submit = () -> {
                login(11L); TenantContextHolder.set(1L); ready.countDown();
                try { assertThat(begin.await(10, TimeUnit.SECONDS)).isTrue(); return leaves.submit(draft.getId(), requestId); }
                finally { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); begin.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getLeaveStatus()).isEqualTo("SUBMITTING");
            assertThat(second.get(10, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(first.get());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_command", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox", Long.class)).isEqualTo(1L);
            verify(workflows, never()).startProcess(any());
        } finally { begin.countDown(); pool.shutdownNow(); }
    }

    @Test void terminalCallbackCannotBindAStartThatHasNotBeenConfirmed() {
        var draft = leaves.create(request());
        leaves.submit(draft.getId(), UUID.randomUUID().toString());
        leaves.receive(result(draft, "completed"));
        assertThat(leaves.details(draft.getId()).getProcessInstanceId()).isNull();
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("SUBMITTING");
    }

    @Test void acceptedSubmissionDoesNotPollThePublicWorkflowApi() {
        var draft = leaves.create(request());
        assertThat(leaves.submit(draft.getId(), UUID.randomUUID().toString()).getLeaveStatus()).isEqualTo("SUBMITTING");
        verifyNoInteractions(workflows);
    }

    @Test void resultCallbacksEmitAuditWithoutAttributingAnonymousSystemWorkToStarter() {
        var draft = started();
        innerRequest.addHeader(com.lotus.bixi.common.core.constant.SecurityConstants.FROM,
                com.lotus.bixi.common.core.constant.SecurityConstants.FROM_IN);
        SecurityContextHolder.clearContext();
        internalController.receive(result(draft, "completed"));
        var cloudLogs = events.stream(com.lotus.bixi.common.log.event.SysLogEvent.class)
                .map(event -> (com.lotus.bixi.upms.api.entity.SysLog) event.getSource())
                .filter(log -> "回写请假审批结果".equals(log.getTitle())).toList();
        assertThat(cloudLogs).singleElement().satisfies(log -> assertThat(log.getCreateBy()).isNull());
        login(22L);
        new com.lotus.bixi.upms.demo.leave.local.LocalWorkflowResultReceiver(leaves).receive(result(draft, "completed"));
        var logs = events.stream(com.lotus.bixi.common.log.event.SysLogEvent.class)
                .map(event -> (com.lotus.bixi.upms.api.entity.SysLog) event.getSource())
                .filter(log -> "回写请假审批结果".equals(log.getTitle())).toList();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).getCreateBy()).isEqualTo(22L);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("APPROVED");
    }

    @Test void internalHttpHandlerRejectsMissingInternalAuthentication() {
        var draft = started();
        assertThatThrownBy(() -> internalController.receive(result(draft, "completed"))).isInstanceOf(AccessDeniedException.class);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("IN_REVIEW");
        innerRequest.addHeader(com.lotus.bixi.common.core.constant.SecurityConstants.FROM,
                com.lotus.bixi.common.core.constant.SecurityConstants.FROM_IN);
        assertThat(internalController.receive(result(draft, "completed")).getCode()).isZero();
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("APPROVED");
    }

    @Test void callbackAfterAnotherTransactionCommitUsesItsOwnCommittingTransaction() {
        var draft = started();
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source))
                .executeWithoutResult(status -> org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void afterCommit() { leaves.receive(result(draft, "completed")); }
                        }));
        assertThat(jdbc.queryForObject("SELECT leave_status FROM demo_leave_request WHERE id=?", String.class, draft.getId()))
                .isEqualTo("APPROVED");
    }

    @Test void callbackIsIdempotentAndRejectsContradictoryTerminalState() {
        var draft = started();
        leaves.receive(result(draft, "rejected")); leaves.receive(result(draft, "rejected"));
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("REJECTED");
        assertThat(leaves.details(draft.getId()).getEndedAt()).isNotNull();
        assertThatThrownBy(() -> leaves.receive(result(draft, "completed"))).isInstanceOf(IllegalStateException.class);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("REJECTED");
    }

    @Test void concurrentDuplicateCallbacksConvergeWithoutContradictoryResults() throws Exception {
        var draft = started();
        var ready = new CountDownLatch(2);
        var begin = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var calls = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    ready.countDown();
                    try { assertThat(begin.await(10, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                    leaves.receive(result(draft, "completed"));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); begin.countDown();
            for (var call : calls) call.get(10, TimeUnit.SECONDS);
            assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("APPROVED");
        }
        finally { begin.countDown(); pool.shutdownNow(); }
    }

    @Test void callbacksRequireExactConfirmedIdentityAndRound() {
        var draft = started();
        var invalid = result(draft, "completed"); invalid.setRound(2);
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setRound(1); invalid.setBusinessKey("forged");
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setBusinessKey(draft.getBusinessKey()); invalid.setProcessInstanceId("different");
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setProcessInstanceId("p-1"); invalid.setStartUserId(33L);
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setStartUserId(11L); invalid.setBusinessTable("other");
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.setBusinessTable("demo_leave_request"); invalid.setProcessKey("other");
        assertThatThrownBy(() -> leaves.receive(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(leaves.details(draft.getId()).getLeaveStatus()).isEqualTo("IN_REVIEW");
    }

    @Test void participantAccessRequiresWorkflowAuthorizationAndMatchingBinding() {
        var draft = started(); login(22L);
        assertThat(leaves.details(draft.getId()).getId()).isEqualTo(draft.getId());
        when(workflows.getApprovalHistory("p-1")).thenReturn(R.ok(List.of()));
        assertThat(leaves.history(draft.getId())).isEmpty();
        login(33L);
        when(workflows.getProcessInstance("p-1")).thenThrow(new AccessDeniedException("not a participant"));
        assertThatThrownBy(() -> leaves.details(draft.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.history(draft.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaves.refresh(draft.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void refreshReconcilesCanceledProcessAndNeverRevertsTerminalState() {
        var draft = started();
        when(workflows.getProcessInstance("p-1")).thenReturn(R.ok(process(draft, "terminated")));
        assertThat(leaves.refresh(draft.getId()).getLeaveStatus()).isEqualTo("CANCELED");
        when(workflows.getProcessInstance("p-1")).thenReturn(R.ok(process(draft, "running")));
        assertThat(leaves.refresh(draft.getId()).getLeaveStatus()).isEqualTo("CANCELED");
    }

    private MockMvc httpWithProjectErrors() {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(
                new com.lotus.bixi.upms.demo.leave.controller.LeaveRequestExceptionAdvice(),
                new GlobalBizExceptionHandler()).build();
    }

    private void recreateTable(String table) throws Exception {
        String schema = new ClassPathResource("sql/01_init_all_tables.sql").getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("CREATE TABLE `?" + table + "`? \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(schema);
        assertThat(matcher.find()).as("canonical schema for %s", table).isTrue();
        String ddl = matcher.group();
        try (var connection = source.getConnection()) {
            if ("MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
                assertThat(connection.getCatalog()).as("only the disposable test schema may be reset")
                        .isEqualTo("workflow_approval_test");
            }
            else {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
                // Keep real MySQL DDL unchanged; omit only unsupported dialect clauses for H2.
                ddl = ddl.replaceFirst("\\) ENGINE[^;]*;", ")")
                        .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+ COLLATE [a-zA-Z0-9_]+", "")
                        .replace(" USING BTREE", "");
            }
        }
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute(ddl);
    }

    private LeaveRequest started() {
        var draft = leaves.create(request());
        leaves.submit(draft.getId(), UUID.randomUUID().toString());
        jdbc.update("UPDATE demo_leave_request SET process_instance_id='p-1', leave_status='IN_REVIEW' WHERE id=?",
                draft.getId());
        when(workflows.getProcessInstance("p-1")).thenReturn(R.ok(process(draft, "running")));
        return leaves.details(draft.getId());
    }
    private static LeaveRequestDTO request() {
        var dto = new LeaveRequestDTO(); dto.setApproverId(22L); dto.setReason("家事");
        dto.setStartDate(LocalDate.of(2026, 10, 1)); dto.setEndDate(LocalDate.of(2026, 10, 2)); return dto;
    }
    private static ProcessInstanceVO process(LeaveRequest draft, String status) {
        var process = new ProcessInstanceVO(); process.setProcessInstanceId("p-1");
        process.setProcessKey("demo_leave_approval"); process.setBusinessTable("demo_leave_request");
        process.setBusinessKey(draft.getBusinessKey()); process.setBusinessId(draft.getId()); process.setStartUserId(11L);
        process.setStatus(status); if (!status.equals("running")) process.setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        return process;
    }
    private static WorkflowResultDTO result(LeaveRequest draft, String status) {
        var result = new WorkflowResultDTO(); result.setEventId("evt-1"); result.setProcessInstanceId("p-1");
        result.setProcessKey("demo_leave_approval"); result.setBusinessTable("demo_leave_request");
        result.setBusinessKey(draft.getBusinessKey()); result.setBusinessId(draft.getId()); result.setRound(1);
        result.setStartUserId(11L); result.setStatus(status); result.setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0)); return result;
    }
    public static java.sql.Timestamp utcTimestamp(int precision) {
        return java.sql.Timestamp.from(java.time.Instant.now());
    }
    private static void login(long id) { login(id, List.of("demo_leave_view", "demo_leave_add", "demo_leave_edit", "demo_leave_del")); }
    private static void login(long id, List<String> permissions) {
        var authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
        var user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null, true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement @EnableMethodSecurity @EnableAspectJAutoProxy
    @Import({LeaveRequestService.class, com.lotus.bixi.upms.demo.leave.command.LeaveSubmitCommandExecutor.class,
            MybatisAutoConfiguration.class,
            com.lotus.bixi.upms.demo.leave.controller.LeaveRequestController.class,
            com.lotus.bixi.upms.demo.leave.controller.LeaveRequestExceptionAdvice.class,
            com.lotus.bixi.upms.demo.leave.controller.LeaveWorkflowResultController.class,
            com.lotus.bixi.common.log.aspect.SysLogAspect.class,
            com.lotus.bixi.common.core.util.SpringContextHolder.class,
            cn.hutool.extra.spring.SpringUtil.class,
            com.lotus.bixi.common.log.config.BixiLogProperties.class})
    @MapperScan({"com.lotus.bixi.upms.demo.leave.mapper", "com.lotus.bixi.upms.mapper"})
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean org.springframework.mock.web.MockHttpServletRequest innerRequest() { return new org.springframework.mock.web.MockHttpServletRequest(); }
        @Bean com.lotus.bixi.common.security.component.BixiSecurityInnerAspect innerAspect(org.springframework.mock.web.MockHttpServletRequest request) {
            return new com.lotus.bixi.common.security.component.BixiSecurityInnerAspect(request);
        }
        @Bean DataSource dataSource() {
            String mysqlUrl = System.getenv("WORKFLOW_TEST_JDBC_URL");
            if (mysqlUrl != null && !mysqlUrl.isBlank()) {
                if (!mysqlUrl.matches("jdbc:mysql://[^/]+/workflow_approval_test(?:\\?.*)?")) {
                    throw new IllegalArgumentException("Leave integration tests require the disposable workflow_approval_test database");
                }
                return new DriverManagerDataSource(mysqlUrl, System.getenv("WORKFLOW_TEST_DB_USER"),
                        System.getenv("WORKFLOW_TEST_DB_PASSWORD"));
            }
            return new DriverManagerDataSource("jdbc:h2:mem:leave-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        }
        @Bean ReliableDeliveryProperties reliableDeliveryProperties() {
            return ReliableDeliveryProperties.defaults();
        }
        @Bean JdbcOutboxStore upmsOutboxStore(DataSource source,
                org.springframework.transaction.PlatformTransactionManager transactionManager,
                ReliableDeliveryProperties properties) {
            return new JdbcOutboxStore(source, transactionManager, properties);
        }
        @Bean WorkflowEventCodec workflowEventCodec() { return new WorkflowEventCodec(); }
        @Bean LeaveWorkflowEventPublisher publisher(JdbcOutboxStore outbox, WorkflowEventCodec codec) {
            return new LeaveWorkflowEventPublisher(outbox, codec);
        }
        @Bean WorkflowService workflows() { return mock(WorkflowService.class); }
        @Bean SysUserService users() { return mock(SysUserService.class); }
        @Bean("pms") PermissionService permissions() { return new PermissionService(); }
        @Bean static PrePostTemplateDefaults defaults() { return new PrePostTemplateDefaults(); }
    }
}
