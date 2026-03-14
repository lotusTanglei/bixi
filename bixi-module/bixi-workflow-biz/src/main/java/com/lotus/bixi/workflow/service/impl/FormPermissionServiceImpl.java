package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormPermissionDTO;
import com.lotus.bixi.workflow.api.dto.RoleFormPermissionDTO;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.vo.FormFieldPermissionVO;
import com.lotus.bixi.workflow.api.vo.FormPermissionVO;
import com.lotus.bixi.workflow.mapper.SysFormPermissionMapper;
import com.lotus.bixi.workflow.service.FormPermissionService;
import com.lotus.bixi.workflow.service.FormService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class FormPermissionServiceImpl extends ServiceImpl<SysFormPermissionMapper, SysFormPermission> implements FormPermissionService {

    private final FormService formService;

    @Override
    public List<FormPermissionVO> listByFormId(Long formId) {
        List<SysFormPermission> permissions = this.lambdaQuery()
                .eq(SysFormPermission::getFormId, formId)
                .list();

        List<FormPermissionVO> result = new ArrayList<>();
        if (CollUtil.isNotEmpty(permissions)) {
            for (SysFormPermission permission : permissions) {
                FormPermissionVO vo = new FormPermissionVO();
                vo.setId(permission.getId());
                vo.setFormId(permission.getFormId());
                vo.setFieldCode(permission.getFieldName());
                vo.setPermType(permission.getPermType());
                vo.setDescription(permission.getFieldLabel());
                vo.setCreateBy(permission.getCreateBy());
                vo.setUpdateBy(permission.getUpdateBy());
                vo.setCreateTime(permission.getCreateTime());
                vo.setUpdateTime(permission.getUpdateTime());
                vo.setTenantId(permission.getTenantId());
                vo.setRemark(permission.getRemark());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean savePermission(FormPermissionDTO dto) {
        SysFormPermission permission = new SysFormPermission();
        permission.setFormId(dto.getFormId());
        permission.setFieldName(dto.getFieldCode());
        permission.setFieldLabel(dto.getDescription());
        permission.setPermType(dto.getPermType());
        permission.setRemark(dto.getRemark());

        if (dto.getId() != null) {
            permission.setId(dto.getId());
            return this.updateById(permission);
        } else {
            return this.save(permission);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        this.removeById(id);
    }

    @Override
    public List<FormFieldPermissionVO> getFieldPermissions(Long formId, Long roleId) {
        List<SysFormPermission> permissions = this.lambdaQuery()
                .eq(SysFormPermission::getFormId, formId)
                .eq(SysFormPermission::getRoleId, roleId)
                .list();

        List<FormFieldPermissionVO> result = new ArrayList<>();
        if (CollUtil.isNotEmpty(permissions)) {
            for (SysFormPermission permission : permissions) {
                FormFieldPermissionVO vo = new FormFieldPermissionVO();
                vo.setFieldCode(permission.getFieldName());
                vo.setFieldLabel(permission.getFieldLabel());
                vo.setPermType(permission.getPermType());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveRolePermission(RoleFormPermissionDTO dto) {
        SysFormPermission permission = new SysFormPermission();
        permission.setRoleId(dto.getRoleId());
        permission.setFormId(dto.getFormPermId());

        if (dto.getId() != null) {
            permission.setId(dto.getId());
            return this.updateById(permission);
        } else {
            return this.save(permission);
        }
    }

    @Override
    public boolean hasPermission(String formKey, String permType) {
        if (StrUtil.isBlank(formKey) || StrUtil.isBlank(permType)) {
            return false;
        }

        WfForm form = formService.getByKey(formKey);
        if (form == null) {
            return false;
        }

        return this.lambdaQuery()
                .eq(SysFormPermission::getFormId, form.getId())
                .eq(SysFormPermission::getPermType, permType)
                .exists();
    }

}
