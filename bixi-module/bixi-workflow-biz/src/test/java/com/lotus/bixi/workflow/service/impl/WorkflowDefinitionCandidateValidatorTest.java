package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowDefinitionCandidateValidatorTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.example/test">
              <process id="candidate-test" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="outer" flowable:candidateUsers="7" flowable:candidateGroups="role:11"/>
                <subProcess id="nested">
                  <userTask id="inner" flowable:candidateUsers="8"/>
                </subProcess>
              </process>
            </definitions>
            """;

    @Test
    void validatesEveryUserTaskFromClasspathBeforeDeployment() throws Exception {
        ResourceLoader resources = mock(ResourceLoader.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        BixiUser actor = user(7L, 42L);
        when(access.currentUser()).thenReturn(actor);
        when(resources.getResource("classpath:processes/test.bpmn20.xml"))
                .thenReturn(new ByteArrayResource(BPMN.getBytes(StandardCharsets.UTF_8)));
        CandidateIdentityQueryService identities = id -> id == 7L
                ? new CandidateIdentity(7L, true, false, 42L)
                : id == 8L ? new CandidateIdentity(8L, true, false, 42L) : null;
        CandidateRoleQueryService roles = id -> id == 11L ? new CandidateRole(11L, true, 42L) : null;
        WorkflowCandidateResolver resolver = new WorkflowCandidateResolver(identities, roles);

        new WorkflowDefinitionCandidateValidator(resources, access, resolver)
                .validateClasspathResource("processes/test.bpmn20.xml");

        verify(access).currentUser();
    }

    @Test
    void rejectsUnknownCandidateUserFromBytesWithStablePrefix() {
        ResourceLoader resources = mock(ResourceLoader.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        when(access.currentUser()).thenReturn(user(7L, 42L));
        CandidateIdentityQueryService identities = id -> id == 7L
                ? new CandidateIdentity(7L, true, false, 42L) : null;
        WorkflowCandidateResolver resolver = new WorkflowCandidateResolver(identities, id -> null);
        String bpmn = BPMN.replace("candidateUsers=\"7\"", "candidateUsers=\"99\"")
                .replace("candidateUsers=\"8\"", "candidateUsers=\"7\"");

        assertThatThrownBy(() -> new WorkflowDefinitionCandidateValidator(resources, access, resolver)
                .validateBytes(bpmn.getBytes(StandardCharsets.UTF_8), "candidate-test.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);
    }

    @Test
    void rejectsMalformedBpmnWithStablePrefix() {
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        when(access.currentUser()).thenReturn(user(7L, 42L));
        WorkflowCandidateResolver resolver = new WorkflowCandidateResolver(id -> null, id -> null);

        assertThatThrownBy(() -> new WorkflowDefinitionCandidateValidator(mock(ResourceLoader.class), access, resolver)
                .validateBytes("not-bpmn".getBytes(StandardCharsets.UTF_8), "broken.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);
    }

    @Test
    void uploadRequiresExactlyOneExecutableProcess() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String twoProcesses = BPMN.replace("</definitions>", """
                <process id="second" isExecutable="true">
                  <startEvent id="secondStart"/>
                  <endEvent id="secondEnd"/>
                  <sequenceFlow id="secondFlow" sourceRef="secondStart" targetRef="secondEnd"/>
                </process>
                </definitions>
                """);

        assertThatThrownBy(() -> validator.validateUpload(
                twoProcesses.getBytes(StandardCharsets.UTF_8), "two.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能包含一个可执行流程");
    }

    @Test
    void uploadRejectsAProcessKeyThatCannotEnterTheTaskNotificationContract() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String invalidProcessKey = BPMN.replace("id=\"candidate-test\"", "id=\"_approval\"")
                .replace(" flowable:candidateUsers=\"7\" flowable:candidateGroups=\"role:11\"", "")
                .replace(" flowable:candidateUsers=\"8\"", "");

        assertThatThrownBy(() -> validator.validateUpload(
                invalidProcessKey.getBytes(StandardCharsets.UTF_8), "invalid-key.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("流程ID");
    }

    @Test
    void uploadRejectsScriptsClassesAndExecutableExpressions() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String base = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.example/test">
                  <process id="unsafe" isExecutable="true">
                    <startEvent id="start"/>
                    %s
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> validator.validateUpload(base.formatted(
                "<scriptTask id=\"run\" scriptFormat=\"groovy\"><script>System.exit(0)</script></scriptTask>")
                .getBytes(StandardCharsets.UTF_8), "script.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("脚本任务");
        assertThatThrownBy(() -> validator.validateUpload(base.formatted(
                "<serviceTask id=\"run\" flowable:class=\"example.UnsafeDelegate\"/>")
                .getBytes(StandardCharsets.UTF_8), "class.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("执行实现");
        assertThatThrownBy(() -> validator.validateUpload(base.formatted(
                "<serviceTask id=\"run\" flowable:expression=\"${runtimeService.deleteDeployment()}\"/>")
                .getBytes(StandardCharsets.UTF_8), "expression.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("执行实现");
    }

    @Test
    void uploadRejectsMethodInvocationInUserTaskAssignmentExpressions() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String bpmn = executableUserTask("${dangerousBean.execute()}", null);

        assertThatThrownBy(() -> validator.validateUpload(
                bpmn.getBytes(StandardCharsets.UTF_8), "unsafe-assignee.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表达式");
    }

    @Test
    void uploadRejectsMethodInvocationInSequenceConditions() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String bpmn = executableUserTask("${approverId}", "${dangerousBean.execute()}");

        assertThatThrownBy(() -> validator.validateUpload(
                bpmn.getBytes(StandardCharsets.UTF_8), "unsafe-condition.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表达式");
    }

    @Test
    void uploadRejectsMethodInvocationInMultiInstanceExpressions() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);
        String bpmn = executableUserTask("${approverId}", null).replace(
                "<userTask id=\"review\" flowable:assignee=\"${approverId}\"/>", """
                <userTask id="review" flowable:assignee="${approverId}">
                  <multiInstanceLoopCharacteristics isSequential="false">
                    <loopCardinality>${dangerousBean.execute()}</loopCardinality>
                  </multiInstanceLoopCharacteristics>
                </userTask>
                """);

        assertThatThrownBy(() -> validator.validateUpload(
                bpmn.getBytes(StandardCharsets.UTF_8), "unsafe-multi-instance.bpmn20.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表达式");
    }

    @Test
    void uploadAcceptsSimpleVariableAndPrimitiveComparisonExpressions() {
        WorkflowDefinitionCandidateValidator validator = validator(42L);

        validator.validateUpload(executableUserTask("${approverId}", "${days <= 3}")
                .getBytes(StandardCharsets.UTF_8), "safe-expression.bpmn20.xml");
    }

    private static String executableUserTask(String assignee, String condition) {
        String conditionElement = condition == null ? "" : """
                <conditionExpression><![CDATA[%s]]></conditionExpression>
                """.formatted(condition);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="https://bixi.example/test">
                  <process id="expression-test" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="review" flowable:assignee="%s"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end">
                      %s
                    </sequenceFlow>
                  </process>
                </definitions>
                """.formatted(assignee, conditionElement);
    }

    private static WorkflowDefinitionCandidateValidator validator(Long tenantId) {
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        when(access.currentUser()).thenReturn(user(7L, tenantId));
        return new WorkflowDefinitionCandidateValidator(mock(ResourceLoader.class), access,
                new WorkflowCandidateResolver(id -> null, id -> null));
    }

    private static BixiUser user(Long id, Long tenantId) {
        return new BixiUser(id, 1L, tenantId, "user-" + id, "unused", null,
                true, true, true, true, new ArrayList<>());
    }
}
