package com.lotus.bixi.workflow.service.impl;

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
@Service
@AllArgsConstructor
public class ProcessDefinitionServiceImpl extends ServiceImpl<WfProcessDefinitionMapper, WfProcessDefinition> implements ProcessDefinitionService {

    private final RepositoryService repositoryService;

    private final org.flowable.engine.ProcessEngine processEngine;

    private final FormService formService;

    @Override
    public List<ProcessDefinitionVO> listLatestVersions() {
        List<org.flowable.engine.repository.ProcessDefinition> flowableDefinitions = repositoryService.createProcessDefinitionQuery()
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
                    .eq(WfProcessDefinition::getProcessDefinitionId, definition.getId()));
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
    public List<ProcessDefinitionVO> listDefinitions(ProcessQueryDTO query) {
        org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery = repositoryService.createProcessDefinitionQuery()
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
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        if (definition == null) {
            return null;
        }

        ProcessDefinitionVO vo = convertToVO(definition);
        WfProcessDefinition wfDefinition = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, definition.getId()));
        if (wfDefinition != null) {
            vo.setId(wfDefinition.getId());
            vo.setCreateTime(wfDefinition.getCreateTime());
            vo.setUpdateTime(wfDefinition.getUpdateTime());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean suspend(String processDefinitionId) {
        try {
            repositoryService.suspendProcessDefinitionById(processDefinitionId);
            this.update(Wrappers.<WfProcessDefinition>lambdaUpdate()
                    .set(WfProcessDefinition::getSuspensionState, WorkflowConstants.SUSPENSION_STATE_SUSPENDED)
                    .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
            return true;
        } catch (Exception e) {
            log.error("Suspend process definition failed", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activate(String processDefinitionId) {
        try {
            repositoryService.activateProcessDefinitionById(processDefinitionId);
            this.update(Wrappers.<WfProcessDefinition>lambdaUpdate()
                    .set(WfProcessDefinition::getSuspensionState, WorkflowConstants.SUSPENSION_STATE_ACTIVE)
                    .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
            return true;
        } catch (Exception e) {
            log.error("Activate process definition failed", e);
            return false;
        }
    }

    @Override
    public byte[] getDiagram(String processDefinitionId) {
        try {
            org.flowable.engine.repository.ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
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
                .eq(WfProcessDefinition::getProcessKey, processKey));

        if (wfDefinition == null || StrUtil.isBlank(wfDefinition.getFormKey())) {
            return null;
        }

        return formService.getRenderInfo(wfDefinition.getFormKey());
    }

    @Override
    public FormRenderVO getFormByDefinitionId(String processDefinitionId) {
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        
        if (definition == null) {
            return null;
        }
        
        return getFormByProcessKey(definition.getKey());
    }

}
