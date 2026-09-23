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

    private static BixiUser user(Long id, Long tenantId) {
        return new BixiUser(id, 1L, tenantId, "user-" + id, "unused", null,
                true, true, true, true, new ArrayList<>());
    }
}
