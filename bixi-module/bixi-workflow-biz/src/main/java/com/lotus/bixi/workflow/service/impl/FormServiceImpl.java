package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormDTO;
import com.lotus.bixi.workflow.api.dto.FormQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.FormVO;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.FormService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class FormServiceImpl extends ServiceImpl<WfFormMapper, WfForm> implements FormService {

    private final WfFormVersionMapper formVersionMapper;
    private final WfProcessDefinitionMapper processDefinitionMapper;
    private final WfProcessInstanceMapper processInstanceMapper;

    @Override
    public WfForm getByKey(String formKey) {
        return this.lambdaQuery()
                .eq(WfForm::getFormKey, formKey)
                .one();
    }

    @Override
    public WfForm getByKeyForUpdate(String formKey) {
        if (StrUtil.isBlank(formKey)) {
            throw new IllegalArgumentException("表单标识不能为空");
        }
        return baseMapper.selectByKeyForUpdate(formKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfForm saveForm(FormDTO dto) {
        WfForm form = new WfForm();
        form.setFormKey(dto.getFormKey());
        form.setFormName(dto.getFormName());
        form.setFormDesc(dto.getDescription());
        form.setFormType(dto.getFormType());
        form.setCategory(dto.getCategory());
        form.setCurrentVersion(0);
        form.setStatus("0");
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
        form.setFormType(dto.getFormType());
        form.setCategory(dto.getCategory());
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
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        Long formId = requireFormId(id);
        WfForm form = baseMapper.selectByIdForUpdate(formId);
        if (form == null) {
            return false;
        }
        requireUnreferenced(List.of(form));
        return super.removeById(formId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        List<Long> formIds = ids.stream().map(FormServiceImpl::requireFormId).distinct().sorted().toList();
        List<WfForm> forms = formIds.stream().map(baseMapper::selectByIdForUpdate).toList();
        if (forms.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("表单不存在");
        }
        requireUnreferenced(forms);
        return super.removeByIds(formIds);
    }

    private void requireUnreferenced(List<WfForm> forms) {
        List<Long> formIds = forms.stream().map(WfForm::getId).toList();
        Long instanceCount = processInstanceMapper.selectCount(Wrappers.<WfProcessInstance>lambdaQuery()
                .in(WfProcessInstance::getFormId, formIds));
        if (instanceCount != null && instanceCount > 0) {
            throw new IllegalStateException("表单已被流程实例引用，不能删除");
        }
        List<String> formKeys = forms.stream().map(WfForm::getFormKey).toList();
        Long definitionCount = processDefinitionMapper.selectCount(Wrappers.<WfProcessDefinition>lambdaQuery()
                .in(WfProcessDefinition::getFormKey, formKeys));
        if (definitionCount != null && definitionCount > 0) {
            throw new IllegalStateException("表单已被流程定义引用，不能删除");
        }
    }

    private static Long requireFormId(Object id) {
        if (!(id instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException("表单ID无效");
        }
        return number.longValue();
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
                .eq(StrUtil.isNotBlank(queryDTO.getFormType()), WfForm::getFormType, queryDTO.getFormType())
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
        return getRenderInfo(formKey, null);
    }

    @Override
    public FormRenderVO getRenderInfo(String formKey, Long formVersionId) {
        WfForm form = this.lambdaQuery()
                .eq(WfForm::getFormKey, formKey)
                .one();
        if (form == null) {
            return null;
        }
        var versionQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfFormVersion>()
                .eq(WfFormVersion::getFormId, form.getId());
        if (formVersionId == null) {
            versionQuery.eq(WfFormVersion::getIsActive, "1")
                    .orderByDesc(WfFormVersion::getVersion)
                    .last("LIMIT 1");
        } else {
            versionQuery.eq(WfFormVersion::getId, formVersionId);
        }
        WfFormVersion formVersion = formVersionMapper.selectOne(versionQuery);
        if (formVersionId != null && formVersion == null) {
            return null;
        }
        FormRenderVO vo = new FormRenderVO();
        vo.setFormId(form.getId());
        vo.setFormKey(form.getFormKey());
        vo.setFormName(form.getFormName());
        if (formVersion != null) {
            vo.setFormVersionId(formVersion.getId());
            vo.setVersion(formVersion.getVersion());
            vo.setSchemaJson(formVersion.getSchemaJson());
        }
        vo.setPermissions(Collections.emptyMap());
        return vo;
    }

}
