package com.lotus.bixi.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import com.lotus.bixi.workflow.command.WorkflowCommandExecutor;
import com.lotus.bixi.workflow.command.WorkflowRequestHasher;
import com.lotus.bixi.common.workflow.config.WorkflowProperties;

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
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowStartRequested;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.ApprovalRecordService;
import com.lotus.bixi.workflow.service.FormDataService;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import com.lotus.bixi.workflow.service.TrustedProcessStarter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.Set;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class ProcessInstanceServiceImpl extends ServiceImpl<WfProcessInstanceMapper, WfProcessInstance>
        implements ProcessInstanceService, TrustedProcessStarter {

    private final RuntimeService runtimeService;

    private final HistoryService historyService;

    private final RepositoryService repositoryService;

    private final ProcessEngine processEngine;

    private final ApprovalRecordService approvalRecordService;

    private final FormDataService formDataService;

    private final WorkflowAccessService access;
    private final WorkflowTerminalEventPublisher terminalEvents;
    private final WorkflowCommandExecutor commands;
    private final WorkflowRequestHasher hasher;
    private final WorkflowProperties workflowProperties;
    private final WorkflowFormRuntimeService runtimeForms;

    private static final String TRUSTED_LEAVE_PROCESS = "demo_leave_approval";
    private static final Set<String> RESERVED_PUBLIC_VARIABLES = Set.of(
            "businessOwner", "businessTable", "businessId", "businessRound", "commandId",
            "startRequestId", "startRequestHash", "tenantId", "tenantScope", "applicant",
            "initiator", "startUserId", "startUserName");

    @Override
    @HasPermission("workflow_process_add")
    public ProcessInstanceVO start(ProcessStartDTO dto) {
        BixiUser user = access.currentUser();
        WorkflowRequestDTO.requireRequestId(dto.getRequestId());
        if (StrUtil.isBlank(dto.getProcessKey())) throw new IllegalArgumentException("流程标识不能为空");
        validatePublicStart(dto);
        ProcessStartDTO normalized = normalizeStart(dto, user);
        Map<String, Object> payload = new HashMap<>();
        payload.put("processKey", normalized.getProcessKey());
        if (normalized.getProcessDefinitionId() != null) {
            payload.put("processDefinitionId", normalized.getProcessDefinitionId());
        }
        payload.put("businessKey", normalized.getBusinessKey());
        payload.put("businessTable", normalized.getBusinessTable());
        payload.put("businessId", normalized.getBusinessId());
        payload.put("title", normalized.getTitle());
        payload.put("variables", normalized.getVariables());
        payload.put("formId", normalized.getFormId());
        payload.put("formData", normalized.getFormDataJson() == null ? null : hasher.parse(normalized.getFormDataJson()));
        var content = hasher.normalize(payload);
        var actor = new WorkflowRequestHasher.Actor(tenantScope(user.getTenantId()), user.getId());
        var input = new WorkflowCommandExecutor.CommandInput(
                dto.getRequestId(), "START", normalized.getProcessKey(), "public", user.getId(), user.getUsername(),
                actor.tenantScope(), content, hasher.hash("START", normalized.getProcessKey(), "public", actor, content), null, null);
        return commands.execute(input, ProcessInstanceVO.class,
                () -> startOnce(prepareNormalizedStart(normalized, user), user));
    }

    @Override
    @HasPermission({"workflow_process_view", "workflow_task_view"})
    public WorkflowCommandVO getCommand(String requestId) {
        return commands.getCommand(requestId);
    }

    /**
     * Trusted internal entry used by the durable workflow inbox. The command identity, actor
     * and business association were authenticated and persisted by UPMS before publication.
     */
    @Transactional
    @Override
    public TrustedProcessStarter.StartResult startTrusted(WorkflowEvent event) {
        if (event == null || !(event.payload() instanceof WorkflowStartRequested requested)) {
            throw new IllegalArgumentException("启动事件无效");
        }
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setRequestId(event.commandId());
        dto.setProcessKey(event.processKey());
        dto.setBusinessTable(event.businessTable());
        dto.setBusinessId(event.businessId());
        dto.setBusinessKey(event.businessKey());
        dto.setTitle(requested.title());
        Map<String, Object> trustedVariables = new HashMap<>();
        trustedVariables.put("approverId", Long.toString(requested.approverId()));
        trustedVariables.put("businessRound", event.round());
        trustedVariables.put("businessOwner", event.sourceOwner());
        trustedVariables.put("businessId", event.businessId());
        trustedVariables.put("businessKey", event.businessKey());
        trustedVariables.put("businessTable", event.businessTable());
        trustedVariables.put("startRequestId", event.commandId());
        trustedVariables.put("startRequestHash", event.payload().requestHash());
        trustedVariables.put("applicant", Long.toString(event.actor().userId()));
        trustedVariables.put("initiator", Long.toString(event.actor().userId()));
        trustedVariables.put("startUserId", Long.toString(event.actor().userId()));
        trustedVariables.put("startUserName", event.actor().username());
        trustedVariables.put("tenantScope", event.tenantScope());
        dto.setVariables(trustedVariables);
        BixiUser actor = new BixiUser(event.actor().userId(), null, tenantId(event.tenantScope()),
                event.actor().username(), "", null,
                true, true, true, true, List.of());
        ProcessStartDTO normalized = normalizeStart(dto, actor);
        Map<String, Object> payload = new HashMap<>();
        payload.put("processKey", normalized.getProcessKey());
        payload.put("businessKey", normalized.getBusinessKey());
        payload.put("businessTable", normalized.getBusinessTable());
        payload.put("businessId", normalized.getBusinessId());
        payload.put("businessRound", event.round());
        payload.put("title", normalized.getTitle());
        payload.put("variables", normalized.getVariables());
        payload.put("businessRequestHash", event.payload().requestHash());
        var content = hasher.normalize(payload);
        var commandActor = new WorkflowRequestHasher.Actor(event.tenantScope(), event.actor().userId());
        String commandHash = hasher.hash("START", event.processKey(), event.sourceOwner(), commandActor, content);
        var input = new WorkflowCommandExecutor.CommandInput(event.commandId(), "START", event.processKey(),
                event.sourceOwner(), event.actor().userId(), event.actor().username(), event.tenantScope(), content,
                commandHash, null, null);
        try {
            return TrustedProcessStarter.StartResult.started(commands.executeTrusted(input, ProcessInstanceVO.class,
                    () -> requireNewBusinessOccurrence(event),
                    () -> startOnce(prepareNormalizedStart(normalized, actor), actor, false,
                            event.payload().requestHash(), event.sourceOwner())));
        }
        catch (BusinessOccurrenceConflictException rejected) {
            return TrustedProcessStarter.StartResult.rejected("BUSINESS_OCCURRENCE_CONFLICT");
        }
        catch (com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException rejected) {
            return TrustedProcessStarter.StartResult.rejected("INVALID_START");
        }
    }

    private void requireNewBusinessOccurrence(WorkflowEvent event) {
        boolean exists = this.lambdaQuery()
                .eq(WfProcessInstance::getBusinessOwner, event.sourceOwner())
                .eq(WfProcessInstance::getBusinessTable, event.businessTable())
                .eq(WfProcessInstance::getBusinessId, event.businessId())
                .eq(WfProcessInstance::getBusinessRound, event.round())
                .exists();
        if (exists) throw new BusinessOccurrenceConflictException();
    }

    private static final class BusinessOccurrenceConflictException extends RuntimeException { }

    private void validatePublicStart(ProcessStartDTO dto) {
        if (TRUSTED_LEAVE_PROCESS.equals(dto.getProcessKey())) {
            throw new IllegalArgumentException("该流程只能通过可信业务入口发起");
        }
        Set<String> allowed = workflowProperties.getPublicStartModels();
        if (allowed == null || !allowed.contains(dto.getProcessKey())) {
            throw new IllegalArgumentException("该流程未开放公共发起");
        }
        if (StrUtil.isNotBlank(dto.getBusinessTable()) || dto.getBusinessId() != null) {
            throw new IllegalArgumentException("公共流程发起不能携带业务关联");
        }
        if (dto.getVariables() != null && dto.getVariables().keySet().stream()
                .anyMatch(RESERVED_PUBLIC_VARIABLES::contains)) {
            String kind = dto.getVariables().containsKey("businessRound")
                    || dto.getVariables().containsKey("businessTable")
                    || dto.getVariables().containsKey("businessId")
                    || dto.getVariables().containsKey("businessOwner") ? "业务关联" : "身份变量";
            throw new IllegalArgumentException("公共流程发起不能携带" + kind);
        }
    }

    private ProcessStartDTO normalizeStart(ProcessStartDTO dto, BixiUser user) {
        ProcessStartDTO normalized = new ProcessStartDTO();
        BeanUtils.copyProperties(dto, normalized);
        normalized.setProcessDefinitionId(StrUtil.isBlank(dto.getProcessDefinitionId())
                ? null : dto.getProcessDefinitionId().trim());
        Map<String, Object> variables = new HashMap<>();
        if (dto.getVariables() != null) variables.putAll(dto.getVariables());
        variables.put("applicant", user.getId().toString());
        variables.put("initiator", user.getId().toString());
        variables.put("startUserId", user.getId().toString());
        variables.put("startUserName", user.getUsername());
        variables.put("startRequestId", dto.getRequestId());
        variables.put("tenantScope", tenantScope(user.getTenantId()));
        variables.remove("tenantId");
        if (variables.containsKey("businessRound")) {
            String round = String.valueOf(variables.get("businessRound"));
            if (!round.matches("[1-9][0-9]{0,8}")) throw new IllegalArgumentException("业务申请轮次无效");
            variables.put("businessRound", Integer.valueOf(round));
        }
        normalized.setVariables(hasher.variables(hasher.normalize(variables)));
        return normalized;
    }

    private PreparedStart prepareNormalizedStart(ProcessStartDTO normalized, BixiUser user) {
        ProcessDefinition definition = resolveDefinition(
                normalized.getProcessKey(), normalized.getProcessDefinitionId(),
                user.getTenantId(), normalized.getRequestId());
        WorkflowFormRuntimeService.PreparedForm form = runtimeForms.prepareStart(
                definition.getId(), normalized.getFormId(), normalized.getFormDataJson());
        if (form == null) {
            normalized.setFormId(null);
            normalized.setFormDataJson(null);
        } else {
            normalized.setFormId(form.formId());
            normalized.setFormDataJson(form.dataJson());
        }
        return new PreparedStart(normalized, definition, form);
    }

    private ProcessDefinition resolveDefinition(String processKey, String processDefinitionId,
                                                Long tenantId, String requestId) {
        if (tenantId != null && tenantId > 0) {
            String tenant = tenantId.toString();
            if (StrUtil.isNotBlank(processDefinitionId)) {
                ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionId(processDefinitionId.trim()).processDefinitionKey(processKey)
                        .processDefinitionTenantId(tenant).active().singleResult();
                if (definition != null) return definition;
                definition = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionId(processDefinitionId.trim()).processDefinitionKey(processKey)
                        .processDefinitionWithoutTenantId().active().singleResult();
                if (definition != null) {
                    log.warn("workflow_legacy_definition_fallback processKey={} tenantId={} requestId={} "
                                    + "candidate tasks will fail closed until the definition is migrated",
                            processKey, tenant, requestId);
                    return definition;
                }
                throw new IllegalArgumentException("流程定义不存在、已挂起或租户不匹配");
            }
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processKey).processDefinitionTenantId(tenant)
                    .latestVersion().active().singleResult();
            if (definition != null) return definition;
            definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processKey).processDefinitionWithoutTenantId()
                    .latestVersion().active().singleResult();
            if (definition != null) {
                log.warn("workflow_legacy_definition_fallback processKey={} tenantId={} requestId={} "
                                + "candidate tasks will fail closed until the definition is migrated",
                        processKey, tenant, requestId);
                return definition;
            }
        } else {
            var query = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processKey).processDefinitionWithoutTenantId().active();
            ProcessDefinition definition = StrUtil.isBlank(processDefinitionId)
                    ? query.latestVersion().singleResult()
                    : query.processDefinitionId(processDefinitionId.trim()).singleResult();
            if (definition != null) return definition;
        }
        throw new IllegalArgumentException("流程定义不存在");
    }

    private ProcessInstanceVO startOnce(PreparedStart prepared, BixiUser user) {
        return startOnce(prepared, user, true, null, null);
    }

    private ProcessInstanceVO startOnce(PreparedStart prepared, BixiUser user, boolean notify, String requestHash,
            String businessOwner) {
        ProcessStartDTO dto = prepared.request();
        Map<String, Object> variables = dto.getVariables();
        Integer businessRound = variables.containsKey("businessRound")
                ? ((Number) variables.get("businessRound")).intValue() : null;
        String previousUser = Authentication.getAuthenticatedUserId();
        ProcessInstance processInstance;
        try {
            Authentication.setAuthenticatedUserId(user.getId().toString());
            var builder = runtimeService.createProcessInstanceBuilder()
                    .processDefinitionId(prepared.definition().getId()).businessKey(dto.getBusinessKey())
                    .name(dto.getTitle()).variables(variables);
            processInstance = builder.start();
        } finally {
            Authentication.setAuthenticatedUserId(previousUser);
        }

        WfProcessInstance wfProcessInstance = new WfProcessInstance();
        wfProcessInstance.setProcessInstanceId(processInstance.getId());
        wfProcessInstance.setStartRequestId(dto.getRequestId());
        wfProcessInstance.setStartRequestHash(requestHash);
        wfProcessInstance.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        wfProcessInstance.setProcessKey(dto.getProcessKey());
        if (prepared.form() != null) {
            wfProcessInstance.setFormId(prepared.form().formId());
            wfProcessInstance.setFormVersionId(prepared.form().formVersionId());
        }
        wfProcessInstance.setBusinessKey(dto.getBusinessKey());
        wfProcessInstance.setBusinessOwner(businessOwner);
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
        if (ended && notify) {
            terminalEvents.publish(wfProcessInstance, WorkflowOutcome.APPROVED,
                    user.getId(), user.getUsername(), dto.getRequestId());
        }

        if (prepared.form() != null) {
            FormDataDTO formDataDTO = new FormDataDTO();
            formDataDTO.setFormId(prepared.form().formId());
            formDataDTO.setFormVersionId(prepared.form().formVersionId());
            formDataDTO.setProcessInstanceId(processInstance.getId());
            formDataDTO.setBusinessKey(dto.getBusinessKey());
            formDataDTO.setDataJson(prepared.form().dataJson());
            formDataDTO.setSubmitUserId(user.getId());
            formDataDTO.setSubmitUserName(user.getUsername());
            formDataDTO.setSubmitTime(LocalDateTime.now());
            formDataService.saveFormData(formDataDTO);
        }

        return notify ? getById(processInstance.getId()) : toView(wfProcessInstance);
    }

    private static long tenantId(String tenantScope) {
        return "default".equals(tenantScope) ? 1L : Long.parseLong(tenantScope);
    }

    private static String tenantScope(Long tenantId) {
        if (tenantId == null || tenantId <= 0) throw new IllegalArgumentException("租户上下文缺失");
        return tenantId.toString();
    }

    private record PreparedStart(ProcessStartDTO request, ProcessDefinition definition,
                                 WorkflowFormRuntimeService.PreparedForm form) {
    }

    private static ProcessInstanceVO toView(WfProcessInstance instance) {
        ProcessInstanceVO view = new ProcessInstanceVO();
        BeanUtils.copyProperties(instance, view);
        return view;
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
        var actor = new WorkflowRequestHasher.Actor(tenantScope(user.getTenantId()), user.getId());
        return commands.execute(new WorkflowCommandExecutor.CommandInput(requestId, "TERMINATE", processInstanceId,
                        "workflow", user.getId(), user.getUsername(), actor.tenantScope(), normalized,
                        hasher.hash("TERMINATE", processInstanceId, "workflow", actor, normalized), null, processInstanceId),
                Boolean.class, () -> {
                    WfProcessInstance locked = baseMapper.selectForUpdate(processInstanceId);
                    if (locked == null) throw new IllegalArgumentException("流程实例不存在");
                    requireStarterLocked(locked, user);
                    return terminateOnce(locked, processInstanceId, reason, user, requestId);
                });
    }

    private boolean terminateOnce(WfProcessInstance instance, String processInstanceId, String reason,
            BixiUser user, String requestId) {
        requireStatus(instance, WorkflowConstants.STATUS_RUNNING);
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        LocalDateTime endedAt = LocalDateTime.now();
        boolean updated = this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_TERMINATED)
                .set(WfProcessInstance::getEndTime, endedAt)
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (!updated) throw new IllegalStateException("流程状态已变更");
        instance.setStatus(WorkflowConstants.STATUS_TERMINATED);
        instance.setEndTime(endedAt);
        terminalEvents.publish(instance, WorkflowOutcome.CANCELED,
                user.getId(), user.getUsername(), requestId);
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
        var actor = new WorkflowRequestHasher.Actor(tenantScope(user.getTenantId()), user.getId());
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
        var actor = new WorkflowRequestHasher.Actor(tenantScope(user.getTenantId()), user.getId());
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
