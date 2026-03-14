package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormDTO;
import com.lotus.bixi.workflow.api.dto.FormQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.FormVO;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.service.FormService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@AllArgsConstructor
public class FormServiceImpl extends ServiceImpl<WfFormMapper, WfForm> implements FormService {

    private final WfFormVersionMapper formVersionMapper;

    @Override
    public WfForm getByKey(String formKey) {
        return this.lambdaQuery()
                .eq(WfForm::getFormKey, formKey)
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfForm saveForm(FormDTO dto) {
        WfForm form = new WfForm();
        form.setFormKey(dto.getFormKey());
        form.setFormName(dto.getFormName());
        form.setFormDesc(dto.getDescription());
        form.setCurrentVersion(1);
        form.setStatus(dto.getStatus());
        form.setRemark(dto.getRemark());
        this.save(form);
        return form;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfForm updateForm(FormDTO dto) {
        WfForm form = this.getById(dto.getId());
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        form.setFormName(dto.getFormName());
        form.setFormDesc(dto.getDescription());
        form.setStatus(dto.getStatus());
        form.setRemark(dto.getRemark());
        this.updateById(form);
        return form;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForm(Long id) {
        this.removeById(id);
    }

    @Override
    public IPage<WfForm> page(Page<WfForm> page, FormQueryDTO query) {
        return this.lambdaQuery()
                .like(StrUtil.isNotBlank(query.getFormKey()), WfForm::getFormKey, query.getFormKey())
                .like(StrUtil.isNotBlank(query.getFormName()), WfForm::getFormName, query.getFormName())
                .eq(StrUtil.isNotBlank(query.getStatus()), WfForm::getStatus, query.getStatus())
                .orderByDesc(WfForm::getCreateTime)
                .page(page);
    }

    @Override
    public IPage<FormVO> listForms(Page<FormVO> page, FormQueryDTO queryDTO) {
        IPage<WfForm> formPage = this.lambdaQuery()
                .like(StrUtil.isNotBlank(queryDTO.getFormKey()), WfForm::getFormKey, queryDTO.getFormKey())
                .like(StrUtil.isNotBlank(queryDTO.getFormName()), WfForm::getFormName, queryDTO.getFormName())
                .eq(StrUtil.isNotBlank(queryDTO.getFormType()), WfForm::getStatus, queryDTO.getFormType())
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), WfForm::getStatus, queryDTO.getStatus())
                .orderByDesc(WfForm::getCreateTime)
                .page(new Page<>(page.getCurrent(), page.getSize()));

        IPage<FormVO> voPage = new Page<>(formPage.getCurrent(), formPage.getSize(), formPage.getTotal());
        voPage.setRecords(formPage.getRecords().stream().map(form -> {
            FormVO vo = new FormVO();
            BeanUtils.copyProperties(form, vo);
            vo.setDescription(form.getFormDesc());
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    public FormVO getByFormKey(String formKey) {
        WfForm form = this.lambdaQuery()
                .eq(WfForm::getFormKey, formKey)
                .one();
        if (form == null) {
            return null;
        }
        FormVO vo = new FormVO();
        BeanUtils.copyProperties(form, vo);
        vo.setDescription(form.getFormDesc());
        return vo;
    }

    @Override
    public FormRenderVO getRenderInfo(String formKey) {
        WfForm form = this.lambdaQuery()
                .eq(WfForm::getFormKey, formKey)
                .one();
        if (form == null) {
            return null;
        }
        WfFormVersion formVersion = formVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfFormVersion>()
                        .eq(WfFormVersion::getFormId, form.getId())
                        .eq(WfFormVersion::getIsActive, "1")
                        .orderByDesc(WfFormVersion::getVersion)
                        .last("LIMIT 1")
        );
        FormRenderVO vo = new FormRenderVO();
        vo.setFormKey(form.getFormKey());
        vo.setFormName(form.getFormName());
        vo.setVersion(form.getCurrentVersion());
        if (formVersion != null) {
            vo.setSchemaJson(formVersion.getSchemaJson());
        }
        vo.setPermissions(Collections.emptyMap());
        return vo;
    }

}
