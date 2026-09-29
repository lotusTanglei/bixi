package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.entity.WfCategory;
import com.lotus.bixi.workflow.service.CategoryService;
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
@RequestMapping("/workflow/category")
@Tag(description = "category", name = "流程分类管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    @HasPermission({"workflow_definition_view", "workflow_process_add"})
    @Operation(summary = "查询分类列表")
    public R<List<WfCategory>> list() {
        return R.ok(categoryService.listAll());
    }

    @GetMapping("/tree")
    @HasPermission({"workflow_definition_view", "workflow_process_add"})
    @Operation(summary = "查询分类树")
    public R<List<WfCategory>> tree() {
        return R.ok(categoryService.tree());
    }

    @PostMapping
    @SysLog("新增分类")
    @HasPermission("workflow_definition_edit")
    @Operation(summary = "新增分类")
    public R<Boolean> save(@Valid @RequestBody WfCategory category) {
        if (category.getId() != null) {
            throw new IllegalArgumentException("新增分类不能指定ID");
        }
        return R.ok(categoryService.saveCategory(category) != null);
    }

    @PutMapping
    @SysLog("更新分类")
    @HasPermission("workflow_definition_edit")
    @Operation(summary = "更新分类")
    public R<Boolean> update(@Valid @RequestBody WfCategory category) {
        if (category.getId() == null) {
            throw new IllegalArgumentException("分类ID不能为空");
        }
        return R.ok(categoryService.saveCategory(category) != null);
    }

    @DeleteMapping("/{id}")
    @SysLog("删除分类")
    @HasPermission("workflow_definition_edit")
    @Operation(summary = "删除分类")
    public R<Boolean> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok(Boolean.TRUE);
    }
}
