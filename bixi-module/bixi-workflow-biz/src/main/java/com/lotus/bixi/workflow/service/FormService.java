package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.FormDTO;
import com.lotus.bixi.workflow.api.dto.FormQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.FormVO;

public interface FormService extends IService<WfForm> {

    WfForm getByKey(String formKey);

    WfForm saveForm(FormDTO dto);

    WfForm updateForm(FormDTO dto);

    void deleteForm(Long id);

    IPage<WfForm> page(Page<WfForm> page, FormQueryDTO query);

    IPage<FormVO> listForms(Page<FormVO> page, FormQueryDTO queryDTO);

    FormVO getByFormKey(String formKey);

    FormRenderVO getRenderInfo(String formKey);

}
