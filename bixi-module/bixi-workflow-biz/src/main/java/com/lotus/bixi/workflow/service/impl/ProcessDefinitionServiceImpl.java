package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.common.security.annotation.HasPermission;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.ProcessDefinitionVO;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import com.lotus.bixi.workflow.service.FormService;
import com.lotus.bixi.workflow.service.FormVersionService;
import com.lotus.bixi.workflow.service.ProcessDefinitionService;
import com.lotus.bixi.common.security.service.BixiUser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.image.ProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class ProcessDefinitionServiceImpl extends ServiceImpl<WfProcessDefinitionMapper, WfProcessDefinition> implements ProcessDefinitionService {

    static final int MAX_BPMN_BYTES = 1024 * 1024;

    private final RepositoryService repositoryService;

    private final org.flowable.engine.ProcessEngine processEngine;

    private final FormService formService;

    private final FormVersionService formVersionService;

    private final WorkflowDefinitionCandidateValidator candidateValidator;

    private final WorkflowAccessService access;

    private final WorkflowFormRuntimeService runtimeForms;

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
        return persistDemoMetadata(definition, tenantId);
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
        return persistDemoMetadata(definition, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @HasPermission("workflow_definition_edit")
    public ProcessDefinitionVO deploy(MultipartFile file, String name, String category, String formKey) {
        String deploymentName = requiredText(name, "流程名称", 255);
        String normalizedCategory = optionalText(category, "流程分类", 64);
        String normalizedFormKey = optionalText(formKey, "表单标识", 255);
        UploadResource upload = readUpload(file);
        WorkflowDefinitionCandidateValidator.ValidatedUpload validated =
                candidateValidator.validateUpload(upload.bytes(), upload.name());
        WfFormVersion boundVersion = requirePublishedForm(normalizedFormKey);

        var builder = repositoryService.createDeployment().name(deploymentName)
                .tenantId(String.valueOf(validated.tenantId()))
                .addBytes(upload.name(), upload.bytes());
        if (normalizedCategory != null) {
            builder.category(normalizedCategory);
        }
        var deployment = builder.deploy();
        List<org.flowable.engine.repository.ProcessDefinition> deployed = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionTenantId(String.valueOf(validated.tenantId()))
                .list();
        if (deployed.size() != 1 || !validated.processKey().equals(deployed.get(0).getKey())) {
            throw new IllegalStateException("部署结果与已校验的 BPMN 定义不一致");
        }

        org.flowable.engine.repository.ProcessDefinition definition = deployed.get(0);
        if (normalizedCategory != null) {
            repositoryService.setProcessDefinitionCategory(definition.getId(), normalizedCategory);
        }
        WfProcessDefinition metadata = new WfProcessDefinition();
        metadata.setProcessDefinitionId(definition.getId());
        metadata.setProcessKey(definition.getKey());
        metadata.setProcessName(definition.getName());
        metadata.setCategory(normalizedCategory != null ? normalizedCategory : definition.getCategory());
        metadata.setVersion(definition.getVersion());
        metadata.setDescription(definition.getDescription());
        metadata.setFormKey(normalizedFormKey);
        metadata.setFormVersionId(boundVersion == null ? null : boundVersion.getId());
        metadata.setDiagramResourceName(definition.getDiagramResourceName());
        metadata.setSuspensionState(definition.isSuspended()
                ? WorkflowConstants.SUSPENSION_STATE_SUSPENDED : WorkflowConstants.SUSPENSION_STATE_ACTIVE);
        metadata.setTenantId(validated.tenantId());
        if (!save(metadata)) {
            throw new IllegalStateException("流程定义元数据保存失败");
        }

        ProcessDefinitionVO result = convertToVO(definition);
        result.setCategory(normalizedCategory != null ? normalizedCategory : definition.getCategory());
        result.setId(metadata.getId());
        result.setCreateTime(metadata.getCreateTime());
        result.setUpdateTime(metadata.getUpdateTime());
        return result;
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
        ProcessQueryDTO criteria = query == null ? new ProcessQueryDTO() : query;
        org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .orderByProcessDefinitionVersion()
                .desc();

        applyDefinitionFilters(definitionQuery, criteria);

        return definitionQuery.list().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @HasPermission("workflow_process_add")
    public List<ProcessDefinitionVO> listStartableDefinitions(ProcessQueryDTO query) {
        ProcessQueryDTO criteria = query == null ? new ProcessQueryDTO() : query;
        org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .latestVersion()
                .active()
                .orderByProcessDefinitionVersion()
                .desc();
        applyDefinitionFilters(definitionQuery, criteria);
        return definitionQuery.list().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    private static void applyDefinitionFilters(
            org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery, ProcessQueryDTO criteria) {
        if (StrUtil.isNotBlank(criteria.getProcessKey())) {
            definitionQuery.processDefinitionKey(criteria.getProcessKey().trim());
        }
        if (StrUtil.isNotBlank(criteria.getProcessName())) {
            definitionQuery.processDefinitionNameLike("%" + criteria.getProcessName().trim() + "%");
        }
        if (StrUtil.isNotBlank(criteria.getCategory())) {
            definitionQuery.processDefinitionCategory(criteria.getCategory().trim());
        }
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

    /**
     * The demo deployment path must keep the extension row in sync with the
     * Flowable definition just like uploaded definitions do. Reliable business
     * start consumes that row to pin the tenant and definition metadata.
     */
    private ProcessDefinitionVO persistDemoMetadata(
            org.flowable.engine.repository.ProcessDefinition definition, Long tenantId) {
        if (definition == null) {
            throw new IllegalStateException("示例流程部署未生成流程定义");
        }
        WfProcessDefinition metadata = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, definition.getId())
                .eq(WfProcessDefinition::getTenantId, tenantId));
        if (metadata == null) {
            metadata = new WfProcessDefinition();
            metadata.setProcessDefinitionId(definition.getId());
            metadata.setTenantId(tenantId);
            metadata.setProcessKey(definition.getKey());
            metadata.setProcessName(definition.getName());
            metadata.setCategory(definition.getCategory());
            metadata.setVersion(definition.getVersion());
            metadata.setDescription(definition.getDescription());
            metadata.setDiagramResourceName(definition.getDiagramResourceName());
            metadata.setSuspensionState(definition.isSuspended()
                    ? WorkflowConstants.SUSPENSION_STATE_SUSPENDED : WorkflowConstants.SUSPENSION_STATE_ACTIVE);
            if (!save(metadata)) {
                throw new IllegalStateException("示例流程定义元数据保存失败");
            }
        }
        else {
            metadata.setProcessKey(definition.getKey());
            metadata.setProcessName(definition.getName());
            metadata.setCategory(definition.getCategory());
            metadata.setVersion(definition.getVersion());
            metadata.setDescription(definition.getDescription());
            metadata.setDiagramResourceName(definition.getDiagramResourceName());
            metadata.setSuspensionState(definition.isSuspended()
                    ? WorkflowConstants.SUSPENSION_STATE_SUSPENDED : WorkflowConstants.SUSPENSION_STATE_ACTIVE);
            updateById(metadata);
        }

        ProcessDefinitionVO result = convertToVO(definition);
        result.setId(metadata.getId());
        result.setCreateTime(metadata.getCreateTime());
        result.setUpdateTime(metadata.getUpdateTime());
        return result;
    }

    @Override
    public FormRenderVO getFormByProcessKey(String processKey) {
        String tenantId = currentTenantId();
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(tenantId)
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        if (definition == null) {
            return null;
        }
        return getFormForDefinition(definition.getId(), Long.valueOf(tenantId));
    }

    @Override
    public FormRenderVO getFormByDefinitionId(String processDefinitionId) {
        String tenantId = currentTenantId();
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(tenantId)
                .processDefinitionId(processDefinitionId)
                .singleResult();

        if (definition == null) {
            return null;
        }

        return getFormForDefinition(definition.getId(), Long.valueOf(tenantId));
    }

    @Override
    @HasPermission("workflow_process_add")
    public FormRenderVO getStartForm(String processDefinitionId) {
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(currentTenantId())
                .processDefinitionId(processDefinitionId)
                .active()
                .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在、已挂起或租户不匹配");
        }
        return runtimeForms.renderStart(definition.getId());
    }

    private FormRenderVO getFormForDefinition(String processDefinitionId, Long tenantId) {
        WfProcessDefinition wfDefinition = this.getOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId)
                .eq(WfProcessDefinition::getTenantId, tenantId));
        if (wfDefinition == null || StrUtil.isBlank(wfDefinition.getFormKey())) {
            return null;
        }
        if (wfDefinition.getFormVersionId() == null) {
            return formService.getRenderInfo(wfDefinition.getFormKey());
        }
        return formService.getRenderInfo(wfDefinition.getFormKey(), wfDefinition.getFormVersionId());
    }

    private String currentTenantId() {
        BixiUser user = access.currentUser();
        if (user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new IllegalArgumentException("当前用户租户无效");
        }
        return String.valueOf(user.getTenantId());
    }

    private WfFormVersion requirePublishedForm(String formKey) {
        if (formKey == null) {
            return null;
        }
        WfForm form = formService.getByKeyForUpdate(formKey);
        if (form == null || !"1".equals(form.getStatus())) {
            throw new IllegalArgumentException("绑定表单未发布或不存在: " + formKey);
        }
        WfFormVersion active = formVersionService.getActiveVersion(form.getId());
        if (active == null || active.getId() == null || !"1".equals(active.getIsActive())) {
            throw new IllegalArgumentException("绑定表单未发布或缺少激活版本: " + formKey);
        }
        return active;
    }

    private static UploadResource readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("BPMN文件不能为空");
        }
        if (file.getSize() > MAX_BPMN_BYTES) {
            throw new IllegalArgumentException("BPMN文件不能超过 1 MiB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("BPMN文件名不能为空");
        }
        fileName = fileName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (fileName.length() > 255 || !(lowerName.endsWith(".bpmn") || lowerName.endsWith(".bpmn20.xml"))) {
            throw new IllegalArgumentException("只允许上传 .bpmn 或 .bpmn20.xml 文件");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BPMN_BYTES + 1);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("BPMN文件不能为空");
            }
            if (bytes.length > MAX_BPMN_BYTES) {
                throw new IllegalArgumentException("BPMN文件不能超过 1 MiB");
            }
            return new UploadResource(fileName, bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("BPMN文件读取失败", ex);
        }
    }

    private static String requiredText(String value, String label, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, label, maxLength);
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

    private record UploadResource(String name, byte[] bytes) {
    }

}
