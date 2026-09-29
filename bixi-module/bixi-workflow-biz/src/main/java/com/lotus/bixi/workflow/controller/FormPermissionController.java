package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormFieldPermissionBatchDTO;
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

@ConditionalOnWorkflowEnabled
@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form/permission")
@Tag(description = "formPermission", name = "表单权限管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormPermissionController {

    private final FormPermissionService formPermissionService;

    @GetMapping("/list/{formId}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询表单权限列表")
    public R<List<FormPermissionVO>> listByFormId(@PathVariable Long formId) {
        return R.ok(formPermissionService.listByFormId(formId));
    }

    @PostMapping
    @SysLog("保存权限配置")
    @HasPermission("workflow_form_edit")
    @Operation(summary = "保存权限配置")
    public R<Boolean> save(@Valid @RequestBody FormPermissionDTO permissionDTO) {
        return R.ok(formPermissionService.savePermission(permissionDTO));
    }

    @DeleteMapping("/{id}")
    @SysLog("删除权限配置")
    @HasPermission("workflow_form_del")
    @Operation(summary = "删除权限配置")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(formPermissionService.removeById(id));
    }

    @GetMapping("/field/{formId}/{roleId}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询字段权限")
    public R<List<FormFieldPermissionVO>> getFieldPermissions(@PathVariable Long formId, @PathVariable Long roleId) {
        return R.ok(formPermissionService.getFieldPermissions(formId, roleId));
    }

    @GetMapping("/field/{formId}/{roleId}/{formVersionId}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询版本作用域字段权限")
    public R<List<FormFieldPermissionVO>> getVersionFieldPermissions(@PathVariable Long formId,
            @PathVariable Long roleId, @PathVariable Long formVersionId,
            @RequestParam(required = false) String processDefinitionId,
            @RequestParam(required = false) String taskDefinitionKey) {
        return R.ok(formPermissionService.getFieldPermissions(formId, roleId, formVersionId,
                processDefinitionId, taskDefinitionKey));
    }

    @PutMapping("/field/{formId}/{roleId}/{formVersionId}")
    @SysLog("保存角色字段权限")
    @HasPermission("workflow_form_edit")
    @Operation(summary = "保存版本作用域字段权限")
    public R<Boolean> saveFieldPermissions(@PathVariable Long formId, @PathVariable Long roleId,
            @PathVariable Long formVersionId, @Valid @RequestBody FormFieldPermissionBatchDTO dto) {
        return R.ok(formPermissionService.saveFieldPermissions(formId, roleId, formVersionId, dto));
    }

    @PostMapping("/role")
    @SysLog("保存角色表单权限")
    @HasPermission("workflow_form_edit")
    @Operation(summary = "保存角色表单权限")
    public R<Boolean> saveRolePermission(@Valid @RequestBody RoleFormPermissionDTO rolePermissionDTO) {
        return R.ok(formPermissionService.saveRolePermission(rolePermissionDTO));
    }
}
