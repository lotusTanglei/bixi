package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Authorization coverage for candidate users and trusted role candidates. */
@SpringJUnitConfig(WfTaskServiceCandidateAuthorizationTest.Config.class)
@TestPropertySource(locations = "classpath:workflow-test.properties", properties = {
        "workflow.enabled=true", "workflow.database-schema-update=true",
        "workflow.async-executor-activate=false", "flowable.check-process-definitions=false"})
class WfTaskServiceCandidateAuthorizationTest {

    @Autowired ProcessEngine engine;
    @Autowired ProcessInstanceService processes;
    @Autowired WfTaskService tasks;
    @Autowired DataSource dataSource;
    @Autowired CandidateState candidates;

    @BeforeEach
    void setup() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_command", "wf_process_instance", "wf_approval_record",
                "wf_process_definition");
        candidates.reset();
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addString("candidate-auth.bpmn20.xml", model()).deploy();
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
    void todoIncludesAssignedUserCandidateAndRoleCandidateOnceWithSanitizedMetadata() {
        String taskId = startTask();

        login(22L);
        TaskVO userCandidate = tasks.todoPage(new Page<>(1, 10), 22L).getRecords().stream()
                .filter(task -> taskId.equals(task.getTaskId())).findFirst().orElseThrow();
        assertThat(userCandidate.getCandidateUsers()).containsExactlyInAnyOrder("22", "44", "45", "46");
        assertThat(userCandidate.getCandidateGroups()).containsExactly("role:11");
        assertThat(userCandidate.isClaimable()).isTrue();

        loginWithRole(33L, 11L);
        assertThat(tasks.todoPage(new Page<>(1, 10), 33L).getRecords())
                .extracting(TaskVO::getTaskId).containsExactly(taskId);
    }

    @Test
    void disabledLockedInactiveAndCrossTenantCandidatesFailClosed() {
        String taskId = startTask();
        candidates.identity(44L, new CandidateIdentity(44L, false, false, 1L));
        candidates.identity(45L, new CandidateIdentity(45L, true, true, 1L));
        candidates.identity(46L, new CandidateIdentity(46L, true, false, 2L));

        for (long userId : List.of(44L, 45L, 46L)) {
            login(userId);
            assertThat(tasks.todoPage(new Page<>(1, 10), userId).getRecords())
                    .extracting(TaskVO::getTaskId).doesNotContain(taskId);
        }
    }

    @Test
    void onlyAnActiveCandidateCanClaimAndClaimUsesAuthenticatedUser() {
        String taskId = startTask();
        loginWithRole(33L, 11L);
        tasks.claim(taskId, 33L, UUID.randomUUID().toString());
        assertThat(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult().getAssignee())
                .isEqualTo("33");

        String secondTask = startTask();
        login(44L);
        candidates.identity(44L, new CandidateIdentity(44L, false, false, 1L));
        assertThatThrownBy(() -> tasks.claim(secondTask, 44L, UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sameGroupNonAssigneeCannotCompleteAssignedTask() {
        String taskId = startTask();
        login(22L);
        tasks.claim(taskId, 22L, UUID.randomUUID().toString());

        loginWithRole(33L, 11L);
        TaskCompleteDTO complete = new TaskCompleteDTO();
        complete.setTaskId(taskId);
        complete.setRequestId(UUID.randomUUID().toString());
        assertThatThrownBy(() -> tasks.complete(complete)).isInstanceOf(AccessDeniedException.class);
    }

    private String startTask() {
        ProcessStartDTO request = new ProcessStartDTO();
        request.setRequestId(UUID.randomUUID().toString());
        request.setProcessKey("candidate-auth");
        request.setBusinessKey(UUID.randomUUID().toString());
        String processInstanceId = processes.start(request).getProcessInstanceId();
        return engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult().getId();
    }

    private static void login(long id) {
        login(id, List.of());
    }

    private static void loginWithRole(long id, long roleId) {
        login(id, List.of("ROLE_" + roleId));
    }

    private static void login(long id, List<String> roles) {
        TenantContextHolder.set(1L);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("workflow_process_add"));
        authorities.add(new SimpleGrantedAuthority("workflow_process_view"));
        authorities.add(new SimpleGrantedAuthority("workflow_task_view"));
        authorities.add(new SimpleGrantedAuthority("workflow_task_edit"));
        roles.stream().map(SimpleGrantedAuthority::new).map(SimpleGrantedAuthority.class::cast).forEach(authorities::add);
        BixiUser user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    private static String model() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.example/test">
                  <process id="candidate-auth" isExecutable="true"><startEvent id="start"/>
                    <sequenceFlow id="toTask" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="审批" flowable:candidateUsers="22,44,45,46,22"
                        flowable:candidateGroups="role:11,role:11"/>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process></definitions>
                """;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(WorkflowApprovalIntegrationTest.Config.class)
    @EnableMethodSecurity
    static class Config {
        @Bean @Primary CandidateState candidateState() { return new CandidateState(); }
        @Bean @Primary CandidateIdentityQueryService candidateIdentities(CandidateState state) { return state::identity; }
        @Bean @Primary CandidateRoleQueryService candidateRoles(CandidateState state) { return state::role; }
    }

    static final class CandidateState {
        private final java.util.Map<Long, CandidateIdentity> identities = new java.util.HashMap<>();
        private final java.util.Map<Long, CandidateRole> roles = new java.util.HashMap<>();

        void reset() {
            identities.clear();
            for (long id : List.of(11L, 22L, 33L, 44L, 45L, 46L)) {
                identities.put(id, new CandidateIdentity(id, true, false, 1L));
            }
            roles.clear();
            roles.put(11L, new CandidateRole(11L, true, 1L));
        }

        void identity(long id, CandidateIdentity identity) { identities.put(id, identity); }
        CandidateIdentity identity(Long id) { return identities.get(id); }
        CandidateRole role(Long id) { return roles.get(id); }
    }
}
