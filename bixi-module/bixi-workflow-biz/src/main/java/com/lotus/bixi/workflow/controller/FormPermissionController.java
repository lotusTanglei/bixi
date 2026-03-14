package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormPermissionDTO;
import com.lotus.bixi.workflow.api.dto.RoleFormPermissionDTO;
import com.lotus.bixi.workflow.api.vo.FormFieldPermissionVO;
import com.lotus.bixi.workflow.api.vo.FormPermissionVO;
import com.lotus.bixi.workflow.service.FormPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form/permission")
@Tag(description = "formPermission", name = "表单权限管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormPermissionController {

    private final FormPermissionService formPermissionService;

    @GetMapping("/list/{formId}")
    @HasPermission("wf_form_perm_view")
    @Operation(summary = "查询表单权限列表")
    public R<List<FormPermissionVO>> listByFormId(@PathVariable Long formId) {
        return R.ok(formPermissionService.listByFormId(formId));
    }

    @PostMapping
    @SysLog("保存权限配置")
    @HasPermission("wf_form_perm_edit")
    @Operation(summary = "保存权限配置")
    public R<Boolean> save(@Valid @RequestBody FormPermissionDTO permissionDTO) {
        return R.ok(formPermissionService.savePermission(permissionDTO));
    }

    @DeleteMapping("/{id}")
    @SysLog("删除权限配置")
    @HasPermission("wf_form_perm_del")
    @Operation(summary = "删除权限配置")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(formPermissionService.removeById(id));
    }

    @GetMapping("/field/{formId}/{roleId}")
    @HasPermission("wf_form_perm_view")
    @Operation(summary = "查询字段权限")
    public R<List<FormFieldPermissionVO>> getFieldPermissions(@PathVariable Long formId, @PathVariable Long roleId) {
        return R.ok(formPermissionService.getFieldPermissions(formId, roleId));
    }

    @PostMapping("/role")
    @SysLog("保存角色表单权限")
    @HasPermission("wf_form_perm_edit")
    @Operation(summary = "保存角色表单权限")
    public R<Boolean> saveRolePermission(@Valid @RequestBody RoleFormPermissionDTO rolePermissionDTO) {
        return R.ok(formPermissionService.saveRolePermission(rolePermissionDTO));
    }
}
