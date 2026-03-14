package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
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
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
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

@Slf4j
@Service
@AllArgsConstructor
public class ProcessInstanceServiceImpl extends ServiceImpl<WfProcessInstanceMapper, WfProcessInstance> implements ProcessInstanceService {

    private final RuntimeService runtimeService;

    private final HistoryService historyService;

    private final RepositoryService repositoryService;

    private final ProcessEngine processEngine;

    private final ApprovalRecordService approvalRecordService;

    private final FormDataService formDataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessStartDTO dto) {
        BixiUser user = SecurityUtils.getUser();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                dto.getProcessKey(),
                dto.getBusinessKey(),
                dto.getVariables()
        );

        WfProcessInstance wfProcessInstance = new WfProcessInstance();
        wfProcessInstance.setProcessInstanceId(processInstance.getId());
        wfProcessInstance.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        wfProcessInstance.setProcessKey(dto.getProcessKey());
        wfProcessInstance.setBusinessKey(dto.getBusinessKey());
        wfProcessInstance.setBusinessTable(dto.getBusinessTable());
        wfProcessInstance.setBusinessId(dto.getBusinessId());
        wfProcessInstance.setTitle(dto.getTitle());
        wfProcessInstance.setStartUserId(user.getId());
        wfProcessInstance.setStartUserName(user.getUsername());
        wfProcessInstance.setStatus(WorkflowConstants.STATUS_RUNNING);
        this.save(wfProcessInstance);

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
    public ProcessInstanceVO getById(String processInstanceId) {
        WfProcessInstance wfProcessInstance = this.getOne(Wrappers.<WfProcessInstance>lambdaQuery()
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));

        if (wfProcessInstance == null) {
            return null;
        }

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
    public IPage<ProcessInstanceVO> page(Page page, ProcessQueryDTO query) {
        IPage<WfProcessInstance> instancePage = this.lambdaQuery()
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
    public IPage<ProcessInstanceVO> myPage(Page page, ProcessQueryDTO query) {
        Long userId = SecurityUtils.getUser().getId();
        query.setStartUserId(userId);
        return page(page, query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean terminate(String processInstanceId, String reason) {
        try {
            runtimeService.deleteProcessInstance(processInstanceId, reason);

            this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                    .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_TERMINATED)
                    .set(WfProcessInstance::getEndTime, LocalDateTime.now())
                    .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
            return true;
        } catch (Exception e) {
            log.error("Terminate process failed", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean suspend(String processInstanceId) {
        try {
            runtimeService.suspendProcessInstanceById(processInstanceId);
            this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                    .set(WfProcessInstance::getStatus, "suspended")
                    .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
            return true;
        } catch (Exception e) {
            log.error("Suspend process failed", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activate(String processInstanceId) {
        try {
            runtimeService.activateProcessInstanceById(processInstanceId);
            this.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                    .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_RUNNING)
                    .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
            return true;
        } catch (Exception e) {
            log.error("Activate process failed", e);
            return false;
        }
    }

    @Override
    public String getProcessDiagram(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        String processDefinitionId;
        List<String> activeActivityIds;
        
        if (processInstance == null) {
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            processDefinitionId = historicProcessInstance.getProcessDefinitionId();
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
    public List<ApprovalRecordVO> getApprovalHistory(String processInstanceId) {
        return approvalRecordService.listByProcessInstanceId(processInstanceId);
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

}
