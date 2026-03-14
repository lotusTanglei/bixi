package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfFormData;
import com.lotus.bixi.workflow.mapper.WfFormDataMapper;
import com.lotus.bixi.workflow.service.FormDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class FormDataServiceImpl extends ServiceImpl<WfFormDataMapper, WfFormData> implements FormDataService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormData(FormDataDTO dto) {
        WfFormData formData = new WfFormData();
        formData.setFormId(dto.getFormId());
        formData.setFormVersion(dto.getFormVersionId() != null ? dto.getFormVersionId().intValue() : null);
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
    public WfFormData getByProcessInstanceId(String processInstanceId) {
        if (StrUtil.isBlank(processInstanceId)) {
            return null;
        }
        return this.lambdaQuery()
                .eq(WfFormData::getProcessInstanceId, processInstanceId)
                .one();
    }

    @Override
    public WfFormData getByTaskId(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return null;
        }
        return this.lambdaQuery()
                .eq(WfFormData::getTaskId, taskId)
                .one();
    }

    @Override
    public List<WfFormData> listByBusinessKey(String businessKey) {
        if (StrUtil.isBlank(businessKey)) {
            return List.of();
        }
        return this.lambdaQuery()
                .eq(WfFormData::getBusinessKey, businessKey)
                .orderByDesc(WfFormData::getCreateTime)
                .list();
    }

}
