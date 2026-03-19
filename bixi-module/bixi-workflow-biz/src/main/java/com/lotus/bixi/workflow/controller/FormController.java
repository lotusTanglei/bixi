package com.lotus.bixi.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormDTO;
import com.lotus.bixi.workflow.api.dto.FormQueryDTO;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.FormVO;
import com.lotus.bixi.workflow.service.FormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form")
@Tag(description = "form", name = "表单管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormController {

    private final FormService formService;

    @GetMapping("/list")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询表单列表")
    public R<IPage<FormVO>> list(Page<FormVO> page, FormQueryDTO queryDTO) {
        return R.ok(formService.listForms(page, queryDTO));
    }

    @GetMapping("/{formKey}")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询表单详情")
    public R<FormVO> getByFormKey(@PathVariable String formKey) {
        return R.ok(formService.getByFormKey(formKey));
    }

    @PostMapping
    @SysLog("创建表单")
    @HasPermission("wf_form_add")
    @Operation(summary = "创建表单")
    public R<Boolean> save(@Valid @RequestBody FormDTO formDTO) {
        formService.saveForm(formDTO);
        return R.ok(Boolean.TRUE);
    }

    @PutMapping
    @SysLog("更新表单")
    @HasPermission("wf_form_edit")
    @Operation(summary = "更新表单")
    public R<Boolean> update(@Valid @RequestBody FormDTO formDTO) {
        formService.updateForm(formDTO);
        return R.ok(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    @SysLog("删除表单")
    @HasPermission("wf_form_del")
    @Operation(summary = "删除表单")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(formService.removeById(id));
    }

    @GetMapping("/render/{formKey}")
    @HasPermission("wf_form_view")
    @Operation(summary = "获取表单渲染信息")
    public R<FormRenderVO> getRenderInfo(@PathVariable String formKey) {
        return R.ok(formService.getRenderInfo(formKey));
    }
}
