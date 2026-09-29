package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.service.FormService;
import com.lotus.bixi.workflow.service.FormVersionService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionUploadTest {

    private static final byte[] BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="https://bixi.example/upload">
              <process id="approval" name="Approval" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="approve"/>
                <endEvent id="end"/>
                <sequenceFlow id="toApprove" sourceRef="start" targetRef="approve"/>
                <sequenceFlow id="toEnd" sourceRef="approve" targetRef="end"/>
              </process>
            </definitions>
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void rejectsMissingEmptyAndOversizedFilesBeforeValidation() {
        Fixture fixture = fixture();
        MockMultipartFile empty = new MockMultipartFile("file", "empty.bpmn20.xml", "text/xml", new byte[0]);
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.bpmn20.xml", "text/xml", new byte[1024 * 1024 + 1]);

        assertThatThrownBy(() -> fixture.service.deploy(null, "Approval", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BPMN文件不能为空");
        assertThatThrownBy(() -> fixture.service.deploy(empty, "Approval", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BPMN文件不能为空");
        assertThatThrownBy(() -> fixture.service.deploy(oversized, "Approval", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 MiB");

        verify(fixture.validator, never()).validateUpload(any(), any());
        verify(fixture.repository, never()).createDeployment();
    }

    @Test
    void rejectsInvalidBpmnAndUnpublishedFormsBeforeRepositoryDeployment() {
        Fixture invalid = fixture();
        doThrow(new IllegalArgumentException(WorkflowCandidateResolver.INVALID_PREFIX + "BPMN解析失败"))
                .when(invalid.validator).validateUpload(any(), any());

        assertThatThrownBy(() -> invalid.service.deploy(file(), "Approval", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);
        verify(invalid.repository, never()).createDeployment();

        Fixture unpublished = fixture();
        when(unpublished.validator.validateUpload(any(), any()))
                .thenReturn(new WorkflowDefinitionCandidateValidator.ValidatedUpload(42L, "approval"));
        when(unpublished.forms.getByKeyForUpdate("leave-form")).thenReturn(form(7L, "0"));

        assertThatThrownBy(() -> unpublished.service.deploy(file(), "Approval", null, "leave-form"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("表单未发布");
        verify(unpublished.repository, never()).createDeployment();
    }

    @Test
    void deploysValidatedVersionAndPersistsImmutableTenantFormMetadata() {
        Fixture fixture = fixture();
        when(fixture.validator.validateUpload(any(), any()))
                .thenReturn(new WorkflowDefinitionCandidateValidator.ValidatedUpload(42L, "approval"));
        when(fixture.forms.getByKeyForUpdate("leave-form")).thenReturn(form(7L, "1"));
        WfFormVersion active = new WfFormVersion();
        active.setId(91L);
        active.setFormId(7L);
        active.setVersion(3);
        active.setIsActive("1");
        when(fixture.versions.getActiveVersion(7L)).thenReturn(active);

        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(fixture.repository.createDeployment()).thenReturn(builder);
        when(builder.name("Approval deployment")).thenReturn(builder);
        when(builder.category("office")).thenReturn(builder);
        when(builder.tenantId("42")).thenReturn(builder);
        when(builder.addBytes("approval.bpmn20.xml", BPMN)).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("deployment-2");
        when(fixture.repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.deploymentId("deployment-2")).thenReturn(query);
        when(query.processDefinitionTenantId("42")).thenReturn(query);
        when(query.list()).thenReturn(List.of(definition));
        when(definition.getId()).thenReturn("approval:2:42");
        when(definition.getKey()).thenReturn("approval");
        when(definition.getName()).thenReturn("Approval");
        when(definition.getCategory()).thenReturn("office");
        when(definition.getVersion()).thenReturn(2);
        when(definition.getResourceName()).thenReturn("approval.bpmn20.xml");
        when(definition.getDeploymentId()).thenReturn("deployment-2");
        when(definition.getDiagramResourceName()).thenReturn("approval.png");
        doReturn(true).when(fixture.service).save(any(WfProcessDefinition.class));

        var result = fixture.service.deploy(file(), " Approval deployment ", " office ", " leave-form ");

        assertThat(result.getProcessDefinitionId()).isEqualTo("approval:2:42");
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getCategory()).isEqualTo("office");
        verify(fixture.repository).setProcessDefinitionCategory("approval:2:42", "office");
        ArgumentCaptor<WfProcessDefinition> metadata = ArgumentCaptor.forClass(WfProcessDefinition.class);
        verify(fixture.service).save(metadata.capture());
        assertThat(metadata.getValue()).satisfies(saved -> {
            assertThat(saved.getProcessDefinitionId()).isEqualTo("approval:2:42");
            assertThat(saved.getProcessKey()).isEqualTo("approval");
            assertThat(saved.getVersion()).isEqualTo(2);
            assertThat(saved.getTenantId()).isEqualTo(42L);
            assertThat(saved.getFormKey()).isEqualTo("leave-form");
            assertThat(saved.getFormVersionId()).isEqualTo(91L);
        });
        InOrder order = inOrder(fixture.validator, fixture.forms, fixture.repository, builder, fixture.service);
        order.verify(fixture.validator).validateUpload(any(), any());
        order.verify(fixture.forms).getByKeyForUpdate("leave-form");
        order.verify(fixture.repository).createDeployment();
        order.verify(builder).deploy();
        order.verify(fixture.service).save(any(WfProcessDefinition.class));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "approval.bpmn20.xml", "text/xml", BPMN);
    }

    private static WfForm form(Long id, String status) {
        WfForm form = new WfForm();
        form.setId(id);
        form.setFormKey("leave-form");
        form.setStatus(status);
        return form;
    }

    private static Fixture fixture() {
        RepositoryService repository = mock(RepositoryService.class);
        FormService forms = mock(FormService.class);
        FormVersionService versions = mock(FormVersionService.class);
        WorkflowDefinitionCandidateValidator validator = mock(WorkflowDefinitionCandidateValidator.class);
        ProcessDefinitionServiceImpl service = spy(new ProcessDefinitionServiceImpl(repository,
                mock(ProcessEngine.class), forms, versions, validator, mock(WorkflowAccessService.class),
                mock(WorkflowFormRuntimeService.class)));
        return new Fixture(repository, forms, versions, validator, service);
    }

    private record Fixture(RepositoryService repository, FormService forms, FormVersionService versions,
                           WorkflowDefinitionCandidateValidator validator, ProcessDefinitionServiceImpl service) {
    }
}
