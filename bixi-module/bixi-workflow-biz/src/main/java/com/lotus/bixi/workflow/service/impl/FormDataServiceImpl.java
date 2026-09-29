package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfFormData;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.mapper.WfFormDataMapper;
import com.lotus.bixi.workflow.service.FormDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

import java.util.List;
import java.util.LinkedHashSet;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class FormDataServiceImpl extends ServiceImpl<WfFormDataMapper, WfFormData> implements FormDataService {

    private final WorkflowAccessService access;
    private final WorkflowFormRuntimeService runtimeForms;
    private final TaskService taskService;
    private final HistoryService historyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormData(FormDataDTO dto) {
        WfFormData formData = new WfFormData();
        formData.setFormId(dto.getFormId());
        formData.setFormVersionId(dto.getFormVersionId());
        formData.setProcessInstanceId(dto.getProcessInstanceId());
        formData.setTaskId(dto.getTaskId());
        formData.setBusinessKey(dto.getBusinessKey());
        formData.setFormDataJson(dto.getDataJson());
        formData.setRemark(dto.getRemark());

        if (dto.getId() != null) {
            formData.setId(dto.getId());
            this.updateById(formData);
        } else {
            this.save(formData);
        }
    }

    @Override
    public FormRenderVO renderByProcessInstanceId(String processInstanceId) {
        if (StrUtil.isBlank(processInstanceId)) {
            return null;
        }
        WfProcessInstance instance = access.requireProcessView(processInstanceId);
        List<Task> active = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
        if (active.size() > 1) {
            throw new IllegalArgumentException("流程存在多个活动任务，请按任务查询表单");
        }
        if (active.size() == 1) {
            Task task = active.get(0);
            return runtimeForms.render(instance, task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        }
        HistoricTaskInstance latest = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).finished()
                .orderByHistoricTaskInstanceEndTime().desc().listPage(0, 1).stream().findFirst().orElse(null);
        return latest == null
                ? runtimeForms.render(instance, instance.getProcessDefinitionId(), WorkflowFormRuntimeService.START_TASK_KEY)
                : runtimeForms.render(instance, latest.getProcessDefinitionId(), latest.getTaskDefinitionKey());
    }

    @Override
    public FormRenderVO renderByTaskId(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return null;
        }
        Task current = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (current != null) {
            WfProcessInstance instance = access.requireProcessView(current.getProcessInstanceId());
            return runtimeForms.render(instance, current.getProcessDefinitionId(), current.getTaskDefinitionKey());
        }
        HistoricTaskInstance historic = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (historic == null) throw new IllegalArgumentException("任务不存在");
        WfProcessInstance instance = access.requireProcessView(historic.getProcessInstanceId());
        return runtimeForms.render(instance, historic.getProcessDefinitionId(), historic.getTaskDefinitionKey());
    }

    @Override
    public List<FormRenderVO> renderByBusinessKey(String businessKey) {
        if (StrUtil.isBlank(businessKey)) {
            return List.of();
        }
        LinkedHashSet<String> processIds = new LinkedHashSet<>(this.lambdaQuery()
                .eq(WfFormData::getBusinessKey, businessKey)
                .orderByDesc(WfFormData::getCreateTime)
                .list().stream().map(WfFormData::getProcessInstanceId).filter(StrUtil::isNotBlank).toList());
        return processIds.stream().map(this::renderByProcessInstanceId).toList();
    }

}
