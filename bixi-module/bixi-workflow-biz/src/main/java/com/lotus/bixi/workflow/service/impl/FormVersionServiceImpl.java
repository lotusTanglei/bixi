package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.service.FormVersionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class FormVersionServiceImpl extends ServiceImpl<WfFormVersionMapper, WfFormVersion> implements FormVersionService {

    private final WfFormMapper formMapper;
    private final WorkflowFormSchema schemas;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfFormVersion createVersion(Long formId, String schemaJson, String changeLog) {
        schemas.compile(schemaJson);
        WfForm form = formMapper.selectByIdForUpdate(formId);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }

        int newVersion = baseMapper.selectMaxVersion(formId) + 1;

        WfFormVersion formVersion = new WfFormVersion();
        formVersion.setFormId(formId);
        formVersion.setVersion(newVersion);
        formVersion.setSchemaJson(schemaJson);
        formVersion.setChangeLog(changeLog);
        formVersion.setIsActive("0");
        this.save(formVersion);

        return formVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfFormVersion activateVersion(Long formId, Integer version) {
        WfForm form = formMapper.selectByIdForUpdate(formId);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        WfFormVersion formVersion = this.lambdaQuery()
                .eq(WfFormVersion::getFormId, formId)
                .eq(WfFormVersion::getVersion, version)
                .one();

        if (formVersion == null) {
            throw new RuntimeException("版本不存在");
        }
        schemas.compile(formVersion.getSchemaJson());

        this.lambdaUpdate()
                .eq(WfFormVersion::getFormId, formId)
                .set(WfFormVersion::getIsActive, "0")
                .update();

        formVersion.setIsActive("1");
        this.updateById(formVersion);

        form.setCurrentVersion(version);
        form.setStatus("1");
        formMapper.updateById(form);

        return formVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfFormVersion rollback(Long formId, Integer version) {
        return activateVersion(formId, version);
    }

    @Override
    public Map<String, Object> diffVersions(Long formId, Integer v1, Integer v2) {
        WfFormVersion version1 = this.lambdaQuery()
                .eq(WfFormVersion::getFormId, formId)
                .eq(WfFormVersion::getVersion, v1)
                .one();

        WfFormVersion version2 = this.lambdaQuery()
                .eq(WfFormVersion::getFormId, formId)
                .eq(WfFormVersion::getVersion, v2)
                .one();

        if (version1 == null || version2 == null) {
            throw new RuntimeException("版本不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("v1", version1);
        result.put("v2", version2);
        result.put("v1Schema", JSONUtil.parseObj(version1.getSchemaJson()));
        result.put("v2Schema", JSONUtil.parseObj(version2.getSchemaJson()));

        return result;
    }

    @Override
    public List<WfFormVersion> listVersions(Long formId) {
        return this.lambdaQuery()
                .eq(WfFormVersion::getFormId, formId)
                .orderByDesc(WfFormVersion::getVersion)
                .list();
    }

    @Override
    public WfFormVersion getActiveVersion(Long formId) {
        return this.lambdaQuery()
                .eq(WfFormVersion::getFormId, formId)
                .eq(WfFormVersion::getIsActive, "1")
                .one();
    }

}
