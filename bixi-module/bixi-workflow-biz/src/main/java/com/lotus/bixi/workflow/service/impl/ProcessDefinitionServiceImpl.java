package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.common.security.annotation.HasPermission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.ProcessDefinitionVO;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import com.lotus.bixi.workflow.service.FormService;
import com.lotus.bixi.workflow.service.ProcessDefinitionService;
import com.lotus.bixi.common.security.service.BixiUser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.image.ProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class ProcessDefinitionServiceImpl extends ServiceImpl<WfProcessDefinitionMapper, WfProcessDefinition> implements ProcessDefinitionService {

    private final RepositoryService repositoryService;

    private final org.flowable.engine.ProcessEngine processEngine;

    private final FormService formService;

    private final WorkflowDefinitionCandidateValidator candidateValidator;

    private final WorkflowAccessService access;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @HasPermission("workflow_definition_edit")
    public ProcessDefinitionVO deployDemo() {
        Long tenantId = candidateValidator.validateClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml");
        var deployment = repositoryService.createDeployment().name("bixi-demo-leave")
                .enableDuplicateFiltering()
                .tenantId(String.valueOf(tenantId))
                .addClasspathResource("processes/demo_leave_approval_v2.bpmn20.xml").deploy();
        var definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).processDefinitionTenantId(String.valueOf(tenantId))
                .processDefinitionKey("demo_leave_approval").singleResult();
        return convertToVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @HasPermission("workflow_definition_edit")
    public ProcessDefinitionVO deployDemoV3() {
        Long tenantId = candidateValidator.validateClasspathResource("processes/demo_leave_approval_v3.bpmn20.xml");
        var deployment = repositoryService.createDeployment().name("bixi-demo-leave-v3")
                .enableDuplicateFiltering()
                .tenantId(String.valueOf(tenantId))
                .addClasspathResource("processes/demo_leave_approval_v3.bpmn20.xml").deploy();
        var definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).processDefinitionTenantId(String.valueOf(tenantId))
                .processDefinitionKey("demo_leave_approval").singleResult();
        return convertToVO(definition);
    }

    @Override
    public List<ProcessDefinitionVO> listLatestVersions() {
        List<org.flowable.engine.repository.ProcessDefinition> flowableDefinitions = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .orderByProcessDefinitionVersion()
                .desc()
                .list();

        Map<String, org.flowable.engine.repository.ProcessDefinition> latestVersions = flowableDefinitions.stream()
                .collect(Collectors.toMap(
                        org.flowable.engine.repository.ProcessDefinition::getKey,
                        definition -> definition,
                        (existing, replacement) -> existing.getVersion() > replacement.getVersion() ? existing : replacement
                ));

        List<ProcessDefinitionVO> result = new ArrayList<>();
        for (org.flowable.engine.repository.ProcessDefinition definition : latestVersions.values()) {
            ProcessDefinitionVO vo = convertToVO(definition);
            WfProcessDefinition wfDefinition = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                    .eq(WfProcessDefinition::getProcessDefinitionId, definition.getId())
                    .eq(WfProcessDefinition::getTenantId, Long.valueOf(currentTenantId())));
            if (wfDefinition != null) {
                vo.setId(wfDefinition.getId());
                vo.setCreateTime(wfDefinition.getCreateTime());
                vo.setUpdateTime(wfDefinition.getUpdateTime());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @HasPermission("workflow_definition_view")
    public List<ProcessDefinitionVO> listDefinitions(ProcessQueryDTO query) {
        org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .orderByProcessDefinitionVersion()
                .desc();

        if (StrUtil.isNotBlank(query.getProcessKey())) {
            definitionQuery.processDefinitionKey(query.getProcessKey());
        }
        if (StrUtil.isNotBlank(query.getProcessName())) {
            definitionQuery.processDefinitionNameLike("%" + query.getProcessName() + "%");
        }

        List<org.flowable.engine.repository.ProcessDefinition> definitions = definitionQuery.list();

        return definitions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public ProcessDefinitionVO getByKey(String processKey) {
        return getByProcessKey(processKey);
    }

    @Override
    public ProcessDefinitionVO getByProcessKey(String processKey) {
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        if (definition == null) {
            return null;
        }

        ProcessDefinitionVO vo = convertToVO(definition);
        WfProcessDefinition wfDefinition = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, definition.getId())
                .eq(WfProcessDefinition::getTenantId, Long.valueOf(currentTenantId())));
        if (wfDefinition != null) {
            vo.setId(wfDefinition.getId());
            vo.setCreateTime(wfDefinition.getCreateTime());
            vo.setUpdateTime(wfDefinition.getUpdateTime());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @HasPermission("workflow_definition_edit")
    public boolean suspend(String processDefinitionId) {
        requireDefinition(processDefinitionId);
        repositoryService.suspendProcessDefinitionById(processDefinitionId);
        this.update(Wrappers.<WfProcessDefinition>lambdaUpdate()
                .set(WfProcessDefinition::getSuspensionState, WorkflowConstants.SUSPENSION_STATE_SUSPENDED)
                .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @HasPermission("workflow_definition_edit")
    public boolean activate(String processDefinitionId) {
        requireDefinition(processDefinitionId);
        repositoryService.activateProcessDefinitionById(processDefinitionId);
        this.update(Wrappers.<WfProcessDefinition>lambdaUpdate()
                .set(WfProcessDefinition::getSuspensionState, WorkflowConstants.SUSPENSION_STATE_ACTIVE)
                .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
        return true;
    }

    @Override
    public byte[] getDiagram(String processDefinitionId) {
        try {
            org.flowable.engine.repository.ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionTenantId(currentTenantId())
                    .processDefinitionId(processDefinitionId)
                    .singleResult();

            if (processDefinition == null) {
                return null;
            }

            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            ProcessDiagramGenerator diagramGenerator = processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();

            try (InputStream inputStream = diagramGenerator.generateDiagram(
                    bpmnModel,
                    "png",
                    new ArrayList<>(),
                    new ArrayList<>(),
                    "宋体",
                    "宋体",
                    "宋体",
                    null,
                    1.0,
                    true)) {

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            log.error("Generate diagram failed", e);
            return null;
        }
    }

    private ProcessDefinitionVO convertToVO(org.flowable.engine.repository.ProcessDefinition definition) {
        ProcessDefinitionVO vo = new ProcessDefinitionVO();
        vo.setProcessDefinitionId(definition.getId());
        vo.setProcessKey(definition.getKey());
        vo.setProcessName(definition.getName());
        vo.setCategory(definition.getCategory());
        vo.setVersion(definition.getVersion());
        vo.setDescription(definition.getDescription());
        vo.setDiagramResourceName(definition.getDiagramResourceName());
        vo.setDeploymentId(definition.getDeploymentId());
        vo.setXmlResourceName(definition.getResourceName());
        vo.setSuspensionState(definition.isSuspended() ? WorkflowConstants.SUSPENSION_STATE_SUSPENDED : WorkflowConstants.SUSPENSION_STATE_ACTIVE);
        return vo;
    }

    @Override
    public FormRenderVO getFormByProcessKey(String processKey) {
        WfProcessDefinition wfDefinition = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessKey, processKey)
                .eq(WfProcessDefinition::getTenantId, Long.valueOf(currentTenantId())));

        if (wfDefinition == null || StrUtil.isBlank(wfDefinition.getFormKey())) {
            return null;
        }

        return formService.getRenderInfo(wfDefinition.getFormKey());
    }

    @Override
    public FormRenderVO getFormByDefinitionId(String processDefinitionId) {
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .processDefinitionId(processDefinitionId)
                .singleResult();
        
        if (definition == null) {
            return null;
        }
        
        return getFormByProcessKey(definition.getKey());
    }

    private String currentTenantId() {
        BixiUser user = access.currentUser();
        if (user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new IllegalArgumentException("当前用户租户无效");
        }
        return String.valueOf(user.getTenantId());
    }

    private void requireDefinition(String processDefinitionId) {
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .processDefinitionTenantId(currentTenantId())
                .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在或租户不匹配");
        }
    }

}
