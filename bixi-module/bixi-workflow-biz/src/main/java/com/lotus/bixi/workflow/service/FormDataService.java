package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfFormData;

import java.util.List;

public interface FormDataService extends IService<WfFormData> {

    void saveFormData(FormDataDTO dto);

    WfFormData getByProcessInstanceId(String processInstanceId);

    WfFormData getByTaskId(String taskId);

    List<WfFormData> listByBusinessKey(String businessKey);

}
