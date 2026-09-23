package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.service.FormService;
import com.lotus.bixi.common.security.service.BixiUser;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class ProcessDefinitionServiceCandidateValidationTest {

    @Test
    void rejectsInvalidCandidatesBeforeRepositoryDeployment() {
        RepositoryService repository = mock(RepositoryService.class);
        WorkflowDefinitionCandidateValidator validator = mock(WorkflowDefinitionCandidateValidator.class);
        doThrow(new IllegalArgumentException(WorkflowCandidateResolver.INVALID_PREFIX + "候选用户不存在或租户不匹配"))
                .when(validator).validateClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml");
        ProcessDefinitionServiceImpl service = new ProcessDefinitionServiceImpl(
                repository, mock(ProcessEngine.class), mock(FormService.class), validator, mock(WorkflowAccessService.class));

        assertThatThrownBy(service::deployDemo)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);

        verify(validator).validateClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml");
        verify(repository, never()).createDeployment();
    }

    @Test
    void scopesDeploymentToAuthenticatedTenantAfterValidation() {
        RepositoryService repository = mock(RepositoryService.class);
        WorkflowDefinitionCandidateValidator validator = mock(WorkflowDefinitionCandidateValidator.class);
        when(validator.validateClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml")).thenReturn(42L);
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        ProcessDefinitionQuery definitions = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repository.createDeployment()).thenReturn(builder);
        when(builder.name("bixi-demo-leave")).thenReturn(builder);
        when(builder.enableDuplicateFiltering()).thenReturn(builder);
        when(builder.tenantId("42")).thenReturn(builder);
        when(builder.addClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml")).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("deployment-1");
        when(repository.createProcessDefinitionQuery()).thenReturn(definitions);
        when(definitions.deploymentId("deployment-1")).thenReturn(definitions);
        when(definitions.processDefinitionTenantId("42")).thenReturn(definitions);
        when(definitions.processDefinitionKey("demo_leave_approval")).thenReturn(definitions);
        when(definitions.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn("definition-1");
        when(definition.getKey()).thenReturn("demo_leave_approval");

        ProcessDefinitionServiceImpl service = new ProcessDefinitionServiceImpl(
                repository, mock(ProcessEngine.class), mock(FormService.class), validator, access(42L));

        service.deployDemo();

        verify(builder).tenantId("42");
    }

    @Test
    void refusesMutationWhenDefinitionBelongsToAnotherTenant() {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery definitions = mock(ProcessDefinitionQuery.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(definitions);
        when(definitions.processDefinitionId("definition-foreign")).thenReturn(definitions);
        when(definitions.processDefinitionTenantId("42")).thenReturn(definitions);
        when(definitions.singleResult()).thenReturn(null);
        ProcessDefinitionServiceImpl service = new ProcessDefinitionServiceImpl(
                repository, mock(ProcessEngine.class), mock(FormService.class),
                mock(WorkflowDefinitionCandidateValidator.class), access(42L));

        assertThatThrownBy(() -> service.suspend("definition-foreign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("流程定义不存在或租户不匹配");

        verify(repository, never()).suspendProcessDefinitionById("definition-foreign");
    }

    private static WorkflowAccessService access(Long tenantId) {
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        when(access.currentUser()).thenReturn(new BixiUser(7L, 1L, tenantId, "user", "unused", null,
                true, true, true, true, new java.util.ArrayList<>()));
        return access;
    }
}
