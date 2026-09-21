package com.lotus.bixi.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import com.lotus.bixi.workflow.command.WorkflowCommandExecutor;
import com.lotus.bixi.workflow.command.WorkflowRequestHasher;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.ApprovalRecordService;
import com.lotus.bixi.workflow.service.FormDataService;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class ProcessInstanceServiceImpl extends ServiceImpl<WfProcessInstanceMapper, WfProcessInstance> implements ProcessInstanceService {

    private final RuntimeService runtimeService;

    private final HistoryService historyService;

    private final RepositoryService repositoryService;

    private final ProcessEngine processEngine;

    private final ApprovalRecordService approvalRecordService;

    private final FormDataService formDataService;

    private final WorkflowAccessService access;
    private final WorkflowResultNotifier results;
    private final WorkflowCommandExecutor commands;
    private final WorkflowRequestHasher hasher;

    @Override
    @HasPermission("workflow_process_add")
    public ProcessInstanceVO start(ProcessStartDTO dto) {
        BixiUser user = access.currentUser();
        WorkflowRequestDTO.requireRequestId(dto.getRequestId());
        if (StrUtil.isBlank(dto.getProcessKey())) throw new IllegalArgumentException("流程标识不能为空");
        ProcessStartDTO normalized = normalizeStart(dto, user);
        Map<String, Object> payload = new HashMap<>();
        payload.put("processKey", normalized.getProcessKey());
        payload.put("businessKey", normalized.getBusinessKey());
        payload.put("businessTable", normalized.getBusinessTable());
        payload.put("businessId", normalized.getBusinessId());
        payload.put("title", normalized.getTitle());
        payload.put("variables", normalized.getVariables());
        payload.put("formId", normalized.getFormId());
        payload.put("formData", normalized.getFormDataJson() == null ? null : hasher.parse(normalized.getFormDataJson()));
        var content = hasher.normalize(payload);
        var actor = new WorkflowRequestHasher.Actor("default", user.getId());
        var input = new WorkflowCommandExecutor.CommandInput(
                dto.getRequestId(), "START", normalized.getProcessKey(), "public", user.getId(), user.getUsername(),
                actor.tenantScope(), content, hasher.hash("START", normalized.getProcessKey(), "public", actor, content), null, null);
        return commands.execute(input, ProcessInstanceVO.class, () -> startOnce(normalized, user));
    }

    @Override
    @HasPermission({"workflow_process_view", "workflow_task_view"})
    public WorkflowCommandVO getCommand(String requestId) {
        return commands.getCommand(requestId);
    }

    private ProcessStartDTO normalizeStart(ProcessStartDTO dto, BixiUser user) {
        ProcessStartDTO normalized = new ProcessStartDTO();
        BeanUtils.copyProperties(dto, normalized);
        Map<String, Object> variables = new HashMap<>();
        if (dto.getVariables() != null) variables.putAll(dto.getVariables());
        variables.put("applicant", user.getId().toString());
        variables.put("initiator", user.getId().toString());
        variables.put("startUserId", user.getId().toString());
        variables.remove("tenantId");
        if (variables.containsKey("businessRound")) {
            String round = String.valueOf(variables.get("businessRound"));
            if (!round.matches("[1-9][0-9]{0,8}")) throw new IllegalArgumentException("业务申请轮次无效");
            variables.put("businessRound", Integer.valueOf(round));
        }
        normalized.setVariables(hasher.variables(hasher.normalize(variables)));
        if (dto.getFormId() != null && StrUtil.isNotBlank(dto.getFormDataJson())) {
            normalized.setFormDataJson(hasher.canonical(hasher.parse(dto.getFormDataJson())));
        } else {
            normalized.setFormId(null);
            normalized.setFormDataJson(null);
        }
        return normalized;
    }

    private ProcessInstanceVO startOnce(ProcessStartDTO dto, BixiUser user) {
        Map<String, Object> variables = dto.getVariables();
        Integer businessRound = variables.containsKey("businessRound")
                ? ((Number) variables.get("businessRound")).intValue() : null;
        String previousUser = Authentication.getAuthenticatedUserId();
        ProcessInstance processInstance;
        try {
            Authentication.setAuthenticatedUserId(user.getId().toString());
            processInstance = runtimeService.createProcessInstanceBuilder()
                    .processDefinitionKey(dto.getProcessKey()).businessKey(dto.getBusinessKey())
                    .name(dto.getTitle()).variables(variables).start();
        } finally {
            Authentication.setAuthenticatedUserId(previousUser);
        }

        WfProcessInstance wfProcessInstance = new WfProcessInstance();
        wfProcessInstance.setProcessInstanceId(processInstance.getId());
        wfProcessInstance.setStartRequestId(dto.getRequestId());
        wfProcessInstance.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        wfProcessInstance.setProcessKey(dto.getProcessKey());
        wfProcessInstance.setBusinessKey(dto.getBusinessKey());
        wfProcessInstance.setBusinessTable(dto.getBusinessTable());
        wfProcessInstance.setBusinessId(dto.getBusinessId());
        wfProcessInstance.setBusinessRound(businessRound);
        wfProcessInstance.setTitle(dto.getTitle());
        wfProcessInstance.setStartUserId(user.getId());
        wfProcessInstance.setStartUserName(user.getUsername());
        boolean ended = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count() == 0;
        wfProcessInstance.setStatus(ended ? WorkflowConstants.STATUS_COMPLETED : WorkflowConstants.STATUS_RUNNING);
        if (ended) {
            wfProcessInstance.setEndTime(LocalDateTime.now());
        }
        this.save(wfProcessInstance);
        if (ended) results.afterCommit(processInstance.getId());

        if (dto.getFormId() != null && StrUtil.isNotBlank(dto.getFormDataJson())) {
            FormDataDTO formDataDTO = new FormDataDTO();
            formDataDTO.setFormId(dto.getFormId());
            formDataDTO.setProcessInstanceId(processInstance.getId());
            formDataDTO.setBusinessKey(dto.getBusinessKey());
            formDataDTO.setDataJson(dto.getFormDataJson());
            formDataDTO.setSubmitUserId(user.getId());
            formDataDTO.setSubmitUserName(user.getUsername());
            formDataDTO.setSubmitTime(LocalDateTime.now());
            formDataService.saveFormData(formDataDTO);
        }

        return getById(processInstance.getId());
    }

    @Override
    @HasPermission("workflow_process_view")
    public ProcessInstanceVO getById(String processInstanceId) {
        WfProcessInstance wfProcessInstance = access.requireProcessView(processInstanceId);

        ProcessInstanceVO vo = new ProcessInstanceVO();
        BeanUtils.copyProperties(wfProcessInstance, vo);

        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance != null) {
            if (historicInstance.getEndTime() != null) {
                vo.setCurrentActivityName("已结束");
            } else {
                vo.setCurrentActivityName(historicInstance.getName());
            }
        }

        return vo;
    }

    @Override
    @HasPermission("workflow_process_view")
    public IPage<ProcessInstanceVO> page(Page page, ProcessQueryDTO query) {
        access.validatePage(page);
        Long userId = access.currentUser().getId();
        if (query.getStartUserId() != null && !userId.equals(query.getStartUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("只能查询本人发起的流程");
        }
        IPage<WfProcessInstance> instancePage = this.lambdaQuery()
                .eq(WfProcessInstance::getStartUserId, userId)
                .eq(StrUtil.isNotBlank(query.getProcessKey()), WfProcessInstance::getProcessKey, query.getProcessKey())
                .eq(StrUtil.isNotBlank(query.getStatus()), WfProcessInstance::getStatus, query.getStatus())
                .eq(query.getStartUserId() != null, WfProcessInstance::getStartUserId, query.getStartUserId())
                .page(page);

        return instancePage.convert(instance -> {
            ProcessInstanceVO vo = new ProcessInstanceVO();
            BeanUtils.copyProperties(instance, vo);
            return vo;
        });
    }

    @Override
    @HasPermission("workflow_process_view")
    public IPage<ProcessInstanceVO> myPage(Page page, ProcessQueryDTO query) {
        Long userId = access.currentUser().getId();
        query.setStartUserId(userId);
        return page(page, query);
    }

    @Override
    @HasPermission("workflow_process_edit")
    public boolean terminate(String processInstanceId, String reason) {
        throw new IllegalArgumentException("requestId必须是标准小写UUID");
    }

    @HasPermission("workflow_process_edit")
    public boolean terminate(String processInstanceId, String reason, String requestId) {
        BixiUser user = access.currentUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        var normalized = hasher.normalize(payload);
        var actor = new WorkflowRequestHasher.Actor(WorkflowCommandExecutor.TENANT_SCOPE, user.getId());
        return commands.execute(new WorkflowCommandExecutor.CommandInput(requestId, "TERMINATE", processInstanceId,
                        "workflow", user.getId(), user.getUsername(), actor.tenantScope(), normalized,
                        hasher.hash("TERMINATE", processInstanceId, "workflow", actor, normalized), null, processInstanceId),
                Boolean.class, () -> {
                    WfProcessInstance locked = baseMapper.selectForUpdate(processInstanceId);
                    if (locked == null) throw new IllegalArgumentException("流程实例不存在");
                    requireStarterLocked(locked, user);
                    return terminateOnce(locked, processInstanceId, reason);
                });
    }

    private boolean terminateOnce(WfProcessInstance instance, String processInstanceId, String reason) {
        requireStatus(instance, WorkflowConstants.STATUS_RUNNING);
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        boolean updated = this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_TERMINATED)
                .set(WfProcessInstance::getEndTime, LocalDateTime.now())
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (!updated) throw new IllegalStateException("流程状态已变更");
        results.afterCommit(processInstanceId);
        return true;
    }

    @Override
    @HasPermission("workflow_process_edit")
    public boolean suspend(String processInstanceId) {
        throw new IllegalArgumentException("requestId必须是标准小写UUID");
    }

    @HasPermission("workflow_process_edit")
    public boolean suspend(String processInstanceId, String requestId) {
        BixiUser user = access.currentUser();
        JsonNode payload = hasher.normalize(Map.of());
        var actor = new WorkflowRequestHasher.Actor(WorkflowCommandExecutor.TENANT_SCOPE, user.getId());
        return commands.execute(new WorkflowCommandExecutor.CommandInput(requestId, "SUSPEND", processInstanceId,
                        "workflow", user.getId(), user.getUsername(), actor.tenantScope(), payload,
                        hasher.hash("SUSPEND", processInstanceId, "workflow", actor, payload), null, processInstanceId),
                Boolean.class, () -> {
                    WfProcessInstance locked = baseMapper.selectForUpdate(processInstanceId);
                    if (locked == null) throw new IllegalArgumentException("流程实例不存在");
                    requireStarterLocked(locked, user);
                    return suspendOnce(locked, processInstanceId);
                });
    }

    private boolean suspendOnce(WfProcessInstance instance, String processInstanceId) {
        requireStatus(instance, WorkflowConstants.STATUS_RUNNING);
        runtimeService.suspendProcessInstanceById(processInstanceId);
        boolean updated = this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_SUSPENDED)
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (!updated) throw new IllegalStateException("流程状态未更新");
        return true;
    }

    @Override
    @HasPermission("workflow_process_edit")
    public boolean activate(String processInstanceId) {
        throw new IllegalArgumentException("requestId必须是标准小写UUID");
    }

    @HasPermission("workflow_process_edit")
    public boolean activate(String processInstanceId, String requestId) {
        BixiUser user = access.currentUser();
        JsonNode payload = hasher.normalize(Map.of());
        var actor = new WorkflowRequestHasher.Actor(WorkflowCommandExecutor.TENANT_SCOPE, user.getId());
        return commands.execute(new WorkflowCommandExecutor.CommandInput(requestId, "ACTIVATE", processInstanceId,
                        "workflow", user.getId(), user.getUsername(), actor.tenantScope(), payload,
                        hasher.hash("ACTIVATE", processInstanceId, "workflow", actor, payload), null, processInstanceId),
                Boolean.class, () -> {
                    WfProcessInstance locked = baseMapper.selectForUpdate(processInstanceId);
                    if (locked == null) throw new IllegalArgumentException("流程实例不存在");
                    requireStarterLocked(locked, user);
                    return activateOnce(locked, processInstanceId);
                });
    }

    private boolean activateOnce(WfProcessInstance instance, String processInstanceId) {
        requireStatus(instance, WorkflowConstants.STATUS_SUSPENDED);
        runtimeService.activateProcessInstanceById(processInstanceId);
        boolean updated = this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_RUNNING)
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (!updated) throw new IllegalStateException("流程状态未更新");
        return true;
    }

    private void requireStatus(WfProcessInstance instance, String expected) {
        if (!expected.equals(instance.getStatus())) {
            throw new IllegalArgumentException("当前流程状态不允许此操作");
        }
    }

    private void requireStarterLocked(WfProcessInstance instance, BixiUser user) {
        if (!user.getId().equals(instance.getStartUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("仅发起人可操作此流程");
        }
    }

    @Override
    @HasPermission("workflow_process_view")
    public String getProcessDiagram(String processInstanceId) {
        WfProcessInstance instance = access.requireProcessView(processInstanceId);
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        String processDefinitionId;
        List<String> activeActivityIds;
        
        if (processInstance == null) {
            processDefinitionId = instance.getProcessDefinitionId();
            activeActivityIds = Collections.emptyList();
        } else {
            processDefinitionId = processInstance.getProcessDefinitionId();
            activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ProcessDiagramGenerator diagramGenerator = processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();
        
        try (InputStream inputStream = diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                activeActivityIds,
                Collections.emptyList(),
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
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Generate diagram failed", e);
            return null;
        }
    }

    @Override
    @HasPermission("workflow_process_view")
    public List<ApprovalRecordVO> getApprovalHistory(String processInstanceId) {
        access.requireProcessView(processInstanceId);
        return approvalRecordService.listByProcessInstanceId(processInstanceId);
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

}
