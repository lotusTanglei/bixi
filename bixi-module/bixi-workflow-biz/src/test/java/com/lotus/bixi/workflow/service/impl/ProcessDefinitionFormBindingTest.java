package com.lotus.bixi.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.service.FormService;
import com.lotus.bixi.workflow.service.FormVersionService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionFormBindingTest {

    @Test
    void definitionIdRendersItsFrozenFormVersionInsteadOfTheCurrentActiveVersion() {
        Fixture fixture = fixture("approval:1:42");

        FormRenderVO result = fixture.service.getFormByDefinitionId("approval:1:42");

        assertThat(result).isSameAs(fixture.frozenRender);
        verify(fixture.forms).getRenderInfo("leave-form", 71L);
        verify(fixture.forms, never()).getRenderInfo("leave-form");
    }

    @Test
    void processKeyResolvesTheLatestFlowableDefinitionBeforeReadingImmutableMetadata() {
        Fixture fixture = fixture("approval:2:42");

        FormRenderVO result = fixture.service.getFormByProcessKey("approval");

        assertThat(result).isSameAs(fixture.frozenRender);
        verify(fixture.query).processDefinitionTenantId("42");
        verify(fixture.query).processDefinitionKey("approval");
        verify(fixture.query).latestVersion();
        verify(fixture.forms).getRenderInfo("leave-form", 71L);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String definitionId) {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionTenantId("42")).thenReturn(query);
        when(query.processDefinitionId(definitionId)).thenReturn(query);
        when(query.processDefinitionKey("approval")).thenReturn(query);
        when(query.latestVersion()).thenReturn(query);
        when(query.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getKey()).thenReturn("approval");

        FormService forms = mock(FormService.class);
        FormRenderVO frozenRender = new FormRenderVO();
        frozenRender.setFormVersionId(71L);
        when(forms.getRenderInfo("leave-form", 71L)).thenReturn(frozenRender);

        WorkflowAccessService access = mock(WorkflowAccessService.class);
        BixiUser user = new BixiUser(7L, 1L, 42L, "user", "unused", null,
                true, true, true, true, List.of());
        when(access.currentUser()).thenReturn(user);

        ProcessDefinitionServiceImpl service = spy(new ProcessDefinitionServiceImpl(repository,
                mock(ProcessEngine.class), forms, mock(FormVersionService.class),
                mock(WorkflowDefinitionCandidateValidator.class), access,
                mock(WorkflowFormRuntimeService.class)));
        WfProcessDefinition metadata = new WfProcessDefinition();
        metadata.setProcessDefinitionId(definitionId);
        metadata.setProcessKey("approval");
        metadata.setFormKey("leave-form");
        metadata.setFormVersionId(71L);
        metadata.setTenantId(42L);
        doReturn(metadata).when(service).getOne(any(Wrapper.class));
        return new Fixture(service, forms, query, frozenRender);
    }

    private record Fixture(ProcessDefinitionServiceImpl service, FormService forms,
                           ProcessDefinitionQuery query, FormRenderVO frozenRender) {
    }
}
