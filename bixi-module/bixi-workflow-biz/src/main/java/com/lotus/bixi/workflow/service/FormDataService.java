package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfFormData;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;

import java.util.List;

public interface FormDataService extends IService<WfFormData> {

    void saveFormData(FormDataDTO dto);

    FormRenderVO renderByProcessInstanceId(String processInstanceId);

    FormRenderVO renderByTaskId(String taskId);

    List<FormRenderVO> renderByBusinessKey(String businessKey);

}
