package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.dto.FormFieldPermissionBatchDTO;
import com.lotus.bixi.workflow.api.dto.FormFieldPermissionItemDTO;
import com.lotus.bixi.workflow.api.dto.FormPermissionDTO;
import com.lotus.bixi.workflow.api.dto.RoleFormPermissionDTO;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.entity.SysRoleFormPermission;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.vo.FormFieldPermissionVO;
import com.lotus.bixi.workflow.api.vo.FormPermissionVO;
import com.lotus.bixi.workflow.mapper.SysFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.SysRoleFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.service.FormPermissionService;
import com.lotus.bixi.workflow.service.FormService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lotus.bixi.common.security.util.SecurityUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@ConditionalOnWorkflowEnabled
@Service
@AllArgsConstructor
public class FormPermissionServiceImpl extends ServiceImpl<SysFormPermissionMapper, SysFormPermission> implements FormPermissionService {

    private final FormService formService;
    private final SysRoleFormPermissionMapper rolePermissionMapper;
    private final WfFormVersionMapper formVersionMapper;
    private final WorkflowFormSchema schemas;

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
                vo.setFieldCode(permission.getFieldCode());
                vo.setPermission(permission.getPermission());
                vo.setPermType(permission.getPermType());
                vo.setDescription(permission.getDescription());
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
        permission.setFieldCode(dto.getFieldCode());
        permission.setPermission(dto.getPermission());
        permission.setDescription(dto.getDescription());
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
        List<Long> granted = rolePermissionMapper.selectList(Wrappers.<SysRoleFormPermission>lambdaQuery()
                        .eq(SysRoleFormPermission::getRoleId, roleId))
                .stream().map(SysRoleFormPermission::getFormPermId).filter(Objects::nonNull).toList();
        if (granted.isEmpty()) return List.of();
        List<FormFieldPermissionVO> result = new ArrayList<>();
        for (SysFormPermission permission : this.lambdaQuery()
                .eq(SysFormPermission::getFormId, formId)
                .in(SysFormPermission::getId, granted)
                .list()) {
            FormFieldPermissionVO vo = new FormFieldPermissionVO();
            vo.setFieldCode(permission.getFieldCode());
            vo.setFieldLabel(permission.getDescription());
            vo.setPermType(normalizePermission(permission.getPermType()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<FormFieldPermissionVO> getFieldPermissions(Long formId, Long roleId, Long formVersionId,
            String processDefinitionId, String taskDefinitionKey) {
        WorkflowFormSchema.Compiled compiled = requireCompiledVersion(formId, formVersionId);
        List<SysFormPermission> scoped = this.list(exactScope(formId, formVersionId,
                processDefinitionId, taskDefinitionKey));
        Set<Long> granted = rolePermissionMapper.selectList(Wrappers.<SysRoleFormPermission>lambdaQuery()
                        .eq(SysRoleFormPermission::getRoleId, roleId))
                .stream().map(SysRoleFormPermission::getFormPermId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, List<SysFormPermission>> rulesByField = new HashMap<>();
        for (SysFormPermission permission : scoped) {
            if (permission.getFieldCode() != null) {
                rulesByField.computeIfAbsent(permission.getFieldCode(), ignored -> new ArrayList<>()).add(permission);
            }
        }

        List<FormFieldPermissionVO> result = new ArrayList<>();
        for (WorkflowFormSchema.FieldRule field : compiled.fields().values()) {
            List<SysFormPermission> rules = rulesByField.getOrDefault(field.name(), List.of());
            String access = rules.isEmpty() ? "edit" : rules.stream()
                    .filter(rule -> granted.contains(rule.getId()))
                    .map(rule -> normalizePermission(rule.getPermType()))
                    .max(java.util.Comparator.comparingInt(FormPermissionServiceImpl::permissionRank))
                    .orElse("hidden");
            FormFieldPermissionVO vo = new FormFieldPermissionVO();
            vo.setFieldCode(field.name());
            vo.setFieldLabel(field.label());
            vo.setFieldType(field.type());
            vo.setPermType(access);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveFieldPermissions(Long formId, Long roleId, Long formVersionId,
            FormFieldPermissionBatchDTO dto) {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
        if (dto == null || dto.getFields() == null || dto.getFields().isEmpty()) {
            throw new IllegalArgumentException("字段权限不能为空");
        }
        WorkflowFormSchema.Compiled compiled = requireCompiledVersion(formId, formVersionId);
        Map<String, FormFieldPermissionItemDTO> requested = new LinkedHashMap<>();
        for (FormFieldPermissionItemDTO item : dto.getFields()) {
            if (item == null || StrUtil.isBlank(item.getFieldCode())
                    || !Set.of("edit", "readonly", "hidden").contains(item.getPermType())) {
                throw new IllegalArgumentException("字段权限不合法");
            }
            if (requested.putIfAbsent(item.getFieldCode(), item) != null) {
                throw new IllegalArgumentException("字段权限重复: " + item.getFieldCode());
            }
        }
        if (!requested.keySet().equals(compiled.fields().keySet())) {
            throw new IllegalArgumentException("必须提交当前版本的全部字段权限");
        }

        String processDefinitionId = normalizeScope(dto.getProcessDefinitionId());
        String taskDefinitionKey = normalizeScope(dto.getTaskDefinitionKey());
        List<SysFormPermission> scoped = this.list(exactScope(formId, formVersionId,
                processDefinitionId, taskDefinitionKey));
        Set<Long> scopedIds = scoped.stream().map(SysFormPermission::getId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!scopedIds.isEmpty()) {
            List<Long> currentAssociations = rolePermissionMapper.selectList(
                            Wrappers.<SysRoleFormPermission>lambdaQuery()
                                    .eq(SysRoleFormPermission::getRoleId, roleId)
                                    .in(SysRoleFormPermission::getFormPermId, scopedIds))
                    .stream().map(SysRoleFormPermission::getId).filter(Objects::nonNull).toList();
            if (!currentAssociations.isEmpty()) {
                rolePermissionMapper.deleteBatchIds(currentAssociations);
            }
        }

        Set<Long> retained = new HashSet<>();
        for (FormFieldPermissionItemDTO item : requested.values()) {
            SysFormPermission rule = scoped.stream()
                    .filter(candidate -> item.getFieldCode().equals(candidate.getFieldCode()))
                    .filter(candidate -> item.getPermType().equals(normalizePermission(candidate.getPermType())))
                    .findFirst().orElse(null);
            if (rule == null) {
                WorkflowFormSchema.FieldRule field = compiled.fields().get(item.getFieldCode());
                rule = new SysFormPermission();
                rule.setFormId(formId);
                rule.setFormVersionId(formVersionId);
                rule.setProcessDefinitionId(processDefinitionId);
                rule.setTaskDefinitionKey(taskDefinitionKey);
                rule.setFieldCode(item.getFieldCode());
                rule.setPermission("workflow_form_field_access");
                rule.setPermType(item.getPermType());
                rule.setDescription(field.label());
                this.save(rule);
                scoped.add(rule);
            }
            retained.add(rule.getId());
            SysRoleFormPermission association = new SysRoleFormPermission();
            association.setRoleId(roleId);
            association.setFormPermId(rule.getId());
            rolePermissionMapper.insert(association);
        }

        List<Long> orphaned = scopedIds.stream().filter(id -> !retained.contains(id))
                .filter(id -> rolePermissionMapper.selectCount(Wrappers.<SysRoleFormPermission>lambdaQuery()
                        .eq(SysRoleFormPermission::getFormPermId, id)) == 0)
                .toList();
        if (!orphaned.isEmpty()) {
            this.removeByIds(orphaned);
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveRolePermission(RoleFormPermissionDTO dto) {
        SysRoleFormPermission permission = new SysRoleFormPermission();
        permission.setRoleId(dto.getRoleId());
        permission.setFormPermId(dto.getFormPermId());
        if (dto.getId() != null) {
            permission.setId(dto.getId());
            return rolePermissionMapper.updateById(permission) > 0;
        } else {
            return rolePermissionMapper.insert(permission) > 0;
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

        Set<Long> roleIds = currentRoleIds();
        if (roleIds.isEmpty()) return false;
        List<Long> granted = rolePermissionMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleFormPermission>()
                                .in(SysRoleFormPermission::getRoleId, roleIds))
                .stream().map(SysRoleFormPermission::getFormPermId).filter(java.util.Objects::nonNull).toList();
        if (granted.isEmpty()) return false;
        return this.lambdaQuery()
                .eq(SysFormPermission::getFormId, form.getId())
                .eq(SysFormPermission::getPermType, permType)
                .in(SysFormPermission::getId, granted)
                .exists();
    }

    private static Set<Long> currentRoleIds() {
        if (SecurityUtils.getAuthentication() == null) return Set.of();
        Set<Long> roleIds = new HashSet<>();
        for (GrantedAuthority authority : SecurityUtils.getAuthentication().getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.matches("ROLE_[1-9][0-9]*")) {
                roleIds.add(Long.parseLong(value.substring("ROLE_".length())));
            }
        }
        return roleIds;
    }

    private WorkflowFormSchema.Compiled requireCompiledVersion(Long formId, Long formVersionId) {
        WfFormVersion version = formVersionMapper.selectById(formVersionId);
        if (version == null || !Objects.equals(formId, version.getFormId())) {
            throw new IllegalArgumentException("表单版本不存在或不属于当前表单");
        }
        return schemas.compile(version.getSchemaJson());
    }

    private static LambdaQueryWrapper<SysFormPermission> exactScope(Long formId, Long formVersionId,
            String processDefinitionId, String taskDefinitionKey) {
        LambdaQueryWrapper<SysFormPermission> query = Wrappers.<SysFormPermission>lambdaQuery()
                .eq(SysFormPermission::getFormId, formId)
                .eq(SysFormPermission::getFormVersionId, formVersionId);
        addExactScope(query, SysFormPermission::getProcessDefinitionId, normalizeScope(processDefinitionId));
        addExactScope(query, SysFormPermission::getTaskDefinitionKey, normalizeScope(taskDefinitionKey));
        return query;
    }

    private static void addExactScope(LambdaQueryWrapper<SysFormPermission> query,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysFormPermission, ?> column, String value) {
        if (value == null) query.isNull(column);
        else query.eq(column, value);
    }

    private static String normalizeScope(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private static String normalizePermission(String permission) {
        if (permission == null) return "hidden";
        return switch (permission.toLowerCase(java.util.Locale.ROOT)) {
            case "edit", "write" -> "edit";
            case "readonly", "read", "view" -> "readonly";
            default -> "hidden";
        };
    }

    private static int permissionRank(String permission) {
        return switch (permission) {
            case "edit" -> 3;
            case "readonly" -> 2;
            default -> 1;
        };
    }

}
